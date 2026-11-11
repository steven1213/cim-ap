package com.cim.spring.support.dispatch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息路径 → 处理器的注册表（启动期扫描装配）。
 *
 * <p>沿用参考实现做对的两件事：① 路径统一归一化后再比较；② **重复注册直接失败**——
 * 两个 DTO 抢同一条路径，若只是「后者覆盖前者」，线上表现是「某类消息时灵时不灵」，
 * 排查成本极高。这里把双方类名一次性打全，让问题在启动期暴露。</p>
 *
 * <p>相比参考实现新增一项校验：处理器泛型参数必须与请求 DTO 一致。参考实现的绑定错误
 * 要到运行期反序列化或强转时才炸，这里在注册期就拦下。</p>
 */
@Slf4j
public class CimMessageRegistry implements SmartInitializingSingleton {

    /** 一条注册项。 */
    public record Entry(String path,
                        Class<?> requestType,
                        Class<? extends CimMessageProcessor<?>> processorType,
                        String method) {
    }

    private final Map<String, Entry> registry = new ConcurrentHashMap<>();
    private final ListableBeanFactory beanFactory;
    private final CimDispatchProperties properties;

    public CimMessageRegistry(ListableBeanFactory beanFactory, CimDispatchProperties properties) {
        this.beanFactory = beanFactory;
        this.properties = properties;
    }

    /** 注册一条消息（重复即失败）。 */
    public void register(Entry entry) {
        Entry prev = registry.putIfAbsent(entry.path(), entry);
        if (prev != null) {
            throw new IllegalStateException("""
                    消息分发路径重复注册（同一路径只能有一个处理器）：
                      路径        : %s
                      已有注册    : %s  →  %s
                      冲突注册    : %s  →  %s
                    处理：改 @CimMessage(path) 显式区分，或删掉其中一个。
                    """.formatted(entry.path(),
                    prev.requestType().getName(), prev.processorType().getName(),
                    entry.requestType().getName(), entry.processorType().getName()));
        }
        validateGenericBinding(entry);
    }

    /** 按路径查找（大小写不敏感）。 */
    public Entry find(String path) {
        return path == null ? null : registry.get(normalize(path));
    }

    /** 当前注册快照（按路径排序，供启动日志与诊断使用）。 */
    public List<Entry> entries() {
        List<Entry> list = new ArrayList<>(registry.values());
        list.sort(Comparator.comparing(Entry::path));
        return list;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!properties.isEnabled()) {
            return;
        }
        for (String pkg : resolveBasePackages()) {
            for (BeanDefinition bd : scanner().findCandidateComponents(pkg)) {
                Entry entry = describe(bd.getBeanClassName());
                if (entry != null) {
                    register(entry);
                }
            }
        }
        log.info("[cim-dispatch] 已注册 {} 条消息：{}", registry.size(),
                entries().stream().map(e -> e.path() + " → " + e.processorType().getSimpleName()).toList());
    }

    /** 扫描根：显式配置优先，否则取应用主类所在包（{@code @SpringBootApplication}）。 */
    private List<String> resolveBasePackages() {
        if (!properties.getBasePackages().isEmpty()) {
            return properties.getBasePackages();
        }
        try {
            return AutoConfigurationPackages.get(beanFactory);
        } catch (IllegalStateException e) {
            throw new IllegalStateException(
                    "无法推断扫描包：应用未注册 AutoConfigurationPackages。"
                            + "请显式配置 cim.api.dispatch.base-packages", e);
        }
    }

    private static ClassPathScanningCandidateComponentProvider scanner() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(CimMessage.class));
        return scanner;
    }

    /** 解析注解为注册项；{@code enabled=false} 的返回 {@code null}（下线但保留代码）。 */
    private static Entry describe(String className) {
        if (className == null) {
            return null;
        }
        Class<?> requestType;
        try {
            requestType = ClassUtils.forName(className, CimMessageRegistry.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("消息 DTO 无法加载：" + className, e);
        }
        CimMessage message = AnnotatedElementUtils.findMergedAnnotation(requestType, CimMessage.class);
        if (message == null) {
            throw new IllegalStateException("缺少 @CimMessage：" + className);
        }
        if (!message.enabled()) {
            log.info("[cim-dispatch] 消息已停用，跳过注册：{}", className);
            return null;
        }
        String path = message.path().isEmpty() ? defaultPath(requestType) : message.path();
        return new Entry(normalize(path), requestType, message.processor(), message.method().name());
    }

    /**
     * 校验处理器声明的泛型参数与请求 DTO 一致。
     *
     * <p>例如「处理 {@code LeaveApplyRequest} 的处理器」被挂到 {@code OvertimeRequest} 上，
     * 运行期会在反序列化/强转处炸；这里直接拒绝。解析不到具体类型（原始类型、复杂继承）时跳过。</p>
     */
    private static void validateGenericBinding(Entry entry) {
        Class<?> bound = ResolvableType
                .forClass(entry.processorType())
                .as(CimMessageProcessor.class)
                .getGeneric(0)
                .resolve();
        if (bound != null && !bound.isAssignableFrom(entry.requestType())) {
            throw new IllegalStateException("""
                    消息处理器的泛型参数与请求类型不匹配：
                      请求类型    : %s
                      处理器      : %s（声明处理 %s）
                    处理：确认 @CimMessage(processor=…) 指向处理该 DTO 的处理器。
                    """.formatted(entry.requestType().getName(), entry.processorType().getName(), bound.getName()));
        }
    }

    /** 路径归一化：补前导斜杠、去空白、统一小写。 */
    public static String normalize(String path) {
        String p = path == null ? "" : path.trim();
        if (p.isEmpty()) {
            return "";
        }
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        return p.toLowerCase(Locale.ROOT);
    }

    /** 由 DTO 类名派生默认路径：{@code LeaveApplyRequest → /leaveApply}。 */
    public static String defaultPath(Class<?> requestType) {
        String name = requestType.getSimpleName();
        if (name.endsWith("Request") && name.length() > "Request".length()) {
            name = name.substring(0, name.length() - "Request".length());
        }
        if (name.isEmpty()) {
            return "/";
        }
        return "/" + Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
