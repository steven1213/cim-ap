package com.cim.spring.support.dispatch;

import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;
import com.cim.spring.support.web.Result;
import com.cim.spring.support.web.SystemException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.ListableBeanFactory;

/**
 * 消息分发器：查表 → 反序列化 → 调用处理器 → **统一包信封**。
 *
 * <p>返回类型固定 {@link Result}，因此「分发入口返回了裸对象」在类型层面就不成立。</p>
 *
 * <p>失败语义与参考实现对齐并简化：</p>
 * <ul>
 *   <li>路径未注册 → {@link BizCode#PARAM_INVALID}（不暴露「有哪些路径」的枚举信息）；</li>
 *   <li>报文不是合法 JSON / 类型不匹配 → {@link BizCode#PARAM_INVALID}；</li>
 *   <li>处理器抛出业务异常 → 原样透传（由全局异常处理器生成标准失败信封）；</li>
 *   <li>其他异常 → {@link SystemException}（对外只给 traceId，不泄露内部细节）。</li>
 * </ul>
 */
@Slf4j
public class CimMessageDispatcher {

    private static final String MDC_PATH = "cim.msg.path";
    private static final String MDC_PROC = "cim.msg.processor";

    private final CimMessageRegistry registry;
    private final ListableBeanFactory beanFactory;
    private final ObjectMapper objectMapper;

    public CimMessageDispatcher(CimMessageRegistry registry,
                                ListableBeanFactory beanFactory,
                                ObjectMapper objectMapper) {
        this.registry = registry;
        this.beanFactory = beanFactory;
        // 消息契约按「只增不改」演进：调用方多传字段不应导致失败，故单独禁用未知字段报错，
        // 且不污染全局 ObjectMapper（copy 一份自用）。
        this.objectMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * 分发一条消息。
     *
     * @param path    路径（如 {@code /leaveApply}），大小写不敏感
     * @param rawJson 原始报文（可为空，空则不反序列化直接给空实例）
     * @return 标准成功信封
     */
    public Result<?> dispatch(String path, String rawJson) {
        CimMessageRegistry.Entry entry = registry.find(path);
        if (entry == null) {
            log.warn("[cim-dispatch] 未注册的消息路径：{}", path);
            throw new BizException(BizCode.PARAM_INVALID, "未知的消息类型：" + path);
        }

        MDC.put(MDC_PATH, entry.path());
        MDC.put(MDC_PROC, entry.processorType().getSimpleName());
        long startNanos = System.nanoTime();
        log.info("[cim-dispatch][START] path={} request={} processor={} bodySize={}",
                entry.path(), entry.requestType().getSimpleName(),
                entry.processorType().getSimpleName(), rawJson == null ? 0 : rawJson.length());
        try {
            Object request = parse(entry, rawJson);
            Object data = processor(entry).process(request);
            log.info("[cim-dispatch][DONE] path={} processor={} costMs={}",
                    entry.path(), entry.processorType().getSimpleName(), elapsedMs(startNanos));
            return Result.ok(data);
        } catch (BizException e) {
            log.warn("[cim-dispatch][FAILED] path={} processor={} code={} costMs={}",
                    entry.path(), entry.processorType().getSimpleName(),
                    e.getBizCode().getCode(), elapsedMs(startNanos));
            throw e;
        } catch (Exception e) {
            log.error("[cim-dispatch][FAILED] path={} processor={} costMs={}",
                    entry.path(), entry.processorType().getSimpleName(), elapsedMs(startNanos), e);
            throw new SystemException("消息处理失败：" + entry.path(), e);
        } finally {
            MDC.remove(MDC_PATH);
            MDC.remove(MDC_PROC);
        }
    }

    private Object parse(CimMessageRegistry.Entry entry, String rawJson) {
        String body = (rawJson == null || rawJson.isBlank()) ? "{}" : rawJson;
        try {
            return objectMapper.readValue(body, entry.requestType());
        } catch (Exception e) {
            log.warn("[cim-dispatch] 报文无法解析为 {}：{}",
                    entry.requestType().getSimpleName(), e.getMessage());
            throw new BizException(BizCode.PARAM_INVALID,
                    "报文格式错误：" + entry.requestType().getSimpleName());
        }
    }

    @SuppressWarnings("unchecked")
    private CimMessageProcessor<Object> processor(CimMessageRegistry.Entry entry) {
        return (CimMessageProcessor<Object>) beanFactory.getBean(entry.processorType());
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
