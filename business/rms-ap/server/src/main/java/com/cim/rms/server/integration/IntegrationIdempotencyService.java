package com.cim.rms.server.integration;

import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 接口幂等去重服务（Req §5.2 IF-COM「幂等」基线）。
 *
 * <p><b>为什么必须有</b>：EAP/MES 侧网络超时或重连会重发同一请求（业界惯例：至少一次投递 + 消费端去重）。
 * 上传接口若不去重，一次重试就多建一个配方版本；在线比对若不去重，会重复挂 Hold、重复报警。</p>
 *
 * <p><b>语义</b>：以 {@code (scope, correlationId)} 为幂等键。<b>首次</b>请求正常执行业务并把回执快照
 * 落库；此后同键请求<b>回放</b>首次快照，不再触达业务逻辑（返回结果与首次完全一致）。</p>
 *
 * <p><b>一致性</b>：快照记录与业务动作在<b>同一事务</b>提交——业务回滚则快照一并回滚，因此不会出现
 * 「缓存里有一条成功回执、但业务其实没落库」的假成功。并发重试由 {@code UNIQUE(scope, idem_key)}
 * 兜底：后到者触发唯一约束冲突，转成 {@link BizCode#PARAM_INVALID} 提示按 correlationId 回查，
 * 不会产生两份业务数据。</p>
 *
 * <p><b>无键降级</b>：请求未携带 {@code correlationId} 时不做去重（改由调用方以内容哈希作键），
 * 此时行为与改造前一致，不引入新的拒绝路径。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntegrationIdempotencyService {

    private final IntegrationIdempotencyRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * 幂等执行：命中即回放，未命中则执行并记录回执快照。
     *
     * @param scope    接口作用域（隔离不同接口的相同 correlationId）
     * @param idemKey  幂等键（correlationId）；空白则不启用去重
     * @param type     回执类型（用于快照反序列化）
     * @param action   业务动作（仅在未命中时执行）
     * @param codeOf   从回执里取结果码（写入 {@code result_code}，便于排查）
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public <T> T execute(String scope, String idemKey, Class<T> type,
                         Supplier<T> action, Function<T, String> codeOf) {
        return execute(scope, idemKey, type, action, codeOf, r -> true);
    }

    /**
     * 幂等执行（可控缓存）：仅当 {@code cacheable} 为真才登记快照。
     *
     * <p>用于区分「确定性业务结果」与「前置闸门拒绝」：闸门拒绝（如资格不符、版本未生效）不是
     * 幂等意义上的终局——运维补登资格后，EAP 用<b>同一个</b> correlationId 重试应当被重新评估，
     * 而不是回放旧拒绝。若不区分，就会出现「补了资格仍被判 NOT_QUALIFIED」的陈旧拒绝陷阱。</p>
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public <T> T execute(String scope, String idemKey, Class<T> type,
                         Supplier<T> action, Function<T, String> codeOf, java.util.function.Predicate<T> cacheable) {
        String key = normalize(idemKey);
        if (key == null) {
            return action.get();
        }
        IntegrationIdempotency hit = repository
                .findFirstByScopeAndIdemKeyAndDeletedFalse(scope, key).orElse(null);
        if (hit != null && hit.getResponseJson() != null && !hit.getResponseJson().isBlank()) {
            try {
                T replayed = objectMapper.readValue(hit.getResponseJson(), type);
                log.info("[if-idem] 重复请求回放 scope={} key={} result={}", scope, key, hit.getResultCode());
                return replayed;
            } catch (Exception e) {
                // 快照反序列化失败（类型演进等）：退化为重新执行，不阻断业务
                log.warn("[if-idem] 回执快照解析失败，改为重新执行 scope={} key={} err={}",
                        scope, key, e.toString());
            }
        }
        T result = action.get();
        if (cacheable == null || cacheable.test(result)) {
            record(scope, key, result, codeOf);
        } else {
            log.info("[if-idem] 结果不缓存（可重试语义）scope={} key={}", scope, key);
        }
        return result;
    }

    /** 幂等键规范化：空白 → {@code null}（不启用去重）；超长截断到列宽内。 */
    private static String normalize(String idemKey) {
        if (idemKey == null || idemKey.isBlank()) {
            return null;
        }
        String k = idemKey.trim();
        return k.length() > 160 ? k.substring(0, 160) : k;
    }

    /** 以「内容哈希」兜底构造幂等键（请求未带 correlationId 时用）。 */
    public static String contentKey(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            sb.append(p == null ? '\u0000' : p).append('\u0001');
        }
        try {
            byte[] d = MessageDigest.getInstance("SHA-256")
                    .digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return "h:" + HexFormat.of().formatHex(d).substring(0, 48);
        } catch (Exception e) {
            return "h:" + Integer.toHexString(sb.toString().hashCode());
        }
    }

    private <T> void record(String scope, String key, T result, Function<T, String> codeOf) {
        IntegrationIdempotency row = new IntegrationIdempotency();
        row.setScope(scope);
        row.setIdemKey(key);
        row.setResultCode(codeOf == null || result == null ? null : codeOf.apply(result));
        try {
            row.setResponseJson(objectMapper.writeValueAsString(result));
        } catch (Exception e) {
            // 无法序列化则不落快照——宁可不去重，也不能因序列化失败阻断业务
            log.warn("[if-idem] 回执序列化失败，跳过去重登记 scope={} key={} err={}", scope, key, e.toString());
            return;
        }
        try {
            repository.saveAndFlush(row);
        } catch (DataIntegrityViolationException e) {
            // 并发重试：另一请求已登记同键 → 本笔不重复执行业务数据，提示按 correlationId 回查
            throw new BizException(BizCode.PARAM_INVALID,
                    "重复请求正在处理中，请按 correlationId 查询结果: " + key);
        }
    }

    /** 运维排查：某接口的去重记录。 */
    @Transactional(readOnly = true)
    public java.util.List<IntegrationIdempotency> list(String scope) {
        return repository.findByScopeAndDeletedFalseOrderByCreateTimeDesc(scope);
    }
}
