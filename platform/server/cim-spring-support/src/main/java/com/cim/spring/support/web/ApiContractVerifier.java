package com.cim.spring.support.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一响应契约的启动期校验器（规约见 {@code docs/platform/server/api-convention.md}）。
 *
 * <p><b>解决的问题</b>：{@code Result} 信封靠「大家都记得写」是保不住的——本仓库在接入本校验器前
 * 就有 7 个端点直接返回裸 {@code String}/{@code Map}/{@code ResponseEntity}，其中一个是前端只能
 * 用 {@code postRaw} 特殊处理的。契约不一致的代价落在前端：每个接口都要先判断「这个到底解不解包」。</p>
 *
 * <p><b>做法</b>：启动收尾（{@link SmartInitializingSingleton}）时反射扫描所有
 * {@code @RestController}（以及仅有类型级 {@code @RequestMapping} 的处理器）的映射方法，返回类型必须是 {@link Result}，否则必须标注 {@link RawResponse} 给出理由。
 * 违规按 {@code cim.api.response.strict}（默认 true）直接启动失败——契约问题在启动期暴露，
 * 而不是等前端拿到裸 JSON。</p>
 *
 * <p><b>为什么用反射扫 Bean 而不是 ArchUnit / 字节码扫描</b>：① 零新依赖；② 拿到的是**真正装配进
 * 容器的**控制器（条件装配、profile 差异都被真实反映）；③ 失败信息可以直接带上违规清单。</p>
 */
@Slf4j
public class ApiContractVerifier implements SmartInitializingSingleton {

    private final ListableBeanFactory beanFactory;
    private final ApiResponseProperties properties;

    public ApiContractVerifier(ListableBeanFactory beanFactory, ApiResponseProperties properties) {
        this.beanFactory = beanFactory;
        this.properties = properties;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!properties.isVerify()) {
            log.warn("[api-contract] 统一响应契约校验已关闭（cim.api.response.verify=false）——"
                    + "仅限存量系统迁移过渡期使用，新代码不应依赖此开关");
            return;
        }

        List<String> violations = new ArrayList<>();
        int controllers = 0;
        int mappings = 0;
        int exemptions = 0;

        // 两类都要收：@RestController 是常规写法；只有 @RequestMapping(type-level) + @ResponseBody 的
        // 处理器（如可选装配的分发入口）同样是 MVC 端点，不能漏检。
        Map<String, Object> beans = new LinkedHashMap<>(beanFactory.getBeansWithAnnotation(RestController.class));
        beanFactory.getBeansWithAnnotation(RequestMapping.class).forEach(beans::putIfAbsent);
        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            Class<?> type = ClassUtils.getUserClass(entry.getValue());
            if (!inScope(type)) {
                continue;
            }
            if (AnnotatedElementUtils.findMergedAnnotation(type, RawResponse.class) != null) {
                exemptions++;
                continue;
            }
            controllers++;

            List<Method> methods = new ArrayList<>();
            ReflectionUtils.doWithMethods(type, methods::add, ApiContractVerifier::isHandlerMethod);
            for (Method method : methods) {
                mappings++;
                if (AnnotatedElementUtils.findMergedAnnotation(method, RawResponse.class) != null) {
                    exemptions++;
                    continue;
                }
                if (!Result.class.isAssignableFrom(method.getReturnType())) {
                    violations.add(String.format("%s#%s() -> %s",
                            type.getSimpleName(), method.getName(), method.getReturnType().getSimpleName()));
                }
            }
        }

        violations.sort(Comparator.naturalOrder());
        if (violations.isEmpty()) {
            log.info("[api-contract] 统一响应契约校验通过：{} 个控制器 / {} 个映射方法 / {} 个已声明例外",
                    controllers, mappings, exemptions);
            return;
        }

        String detail = String.join("\n  - ", violations);
        String message = "统一响应契约违规（返回类型必须是 Result，或标注 @RawResponse 并给出理由）：\n  - "
                + detail + "\n口径见 docs/platform/server/api-convention.md";
        if (properties.isStrict()) {
            throw new IllegalStateException(message);
        }
        log.error("[api-contract] {}", message);
    }

    /** 是否属于校验范围（默认只校验 {@code com.cim} 自有代码，跳过框架自带控制器）。 */
    private boolean inScope(Class<?> type) {
        String pkg = type.getPackageName();
        if (properties.getIncludePackages().isEmpty()) {
            return !pkg.startsWith("org.springframework");
        }
        return properties.getIncludePackages().stream().anyMatch(pkg::startsWith);
    }

    /** 只校验真正的 HTTP 映射方法；跳过 {@code @ExceptionHandler}/{@code @InitBinder} 等 Spring MVC 回调。 */
    private static boolean isHandlerMethod(Method method) {
        if (method.isBridge() || method.isSynthetic()) {
            return false;
        }
        if (!AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)) {
            return false;
        }
        return AnnotatedElementUtils.findMergedAnnotation(method, ExceptionHandler.class) == null
                && AnnotatedElementUtils.findMergedAnnotation(method, InitBinder.class) == null
                && AnnotatedElementUtils.findMergedAnnotation(method, ModelAttribute.class) == null;
    }
}
