package com.cim.cache.support;

import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/**
 * 缓存键生成器：组合「命名空间 + SpEL 求值」，产出稳定、可读的键。
 *
 * <p>键格式（不含全局前缀，全局前缀由底层存储施加）：{@code {namespace}:{spelKey}}。
 * SpEL 上下文暴露参数变量 {@code #p0,#p1,...} 与 {@code #args}（数组），例如：
 * <pre>
 *   key = "#id"          // 若编译带 -parameters 也可用形参名；否则用 #p0
 *   key = "#p0"          // 第一个参数
 *   key = "#args[0].id"  // 第一个参数的属性
 * </pre>
 * 未指定 key 时，按参数值拼接生成兜底键。</p>
 */
public class CacheKeyGenerator {

    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final String globalPrefix;

    public CacheKeyGenerator(String globalPrefix) {
        this.globalPrefix = (globalPrefix == null || globalPrefix.isBlank()) ? "" : globalPrefix.trim();
    }

    /**
     * 生成完整键（含全局前缀）。
     */
    public String generate(String namespace, String keyExpression, Object[] args) {
        String spelKey = evaluateKey(keyExpression, args);
        String ns = (namespace == null || namespace.isBlank()) ? "default" : namespace.trim();
        return globalPrefix.isEmpty() ? (ns + ":" + spelKey) : (globalPrefix + ":" + ns + ":" + spelKey);
    }

    private String evaluateKey(String expression, Object[] args) {
        if (expression == null || expression.isBlank()) {
            return defaultKey(args);
        }
        try {
            StandardEvaluationContext ctx = new StandardEvaluationContext();
            if (args != null) {
                for (int i = 0; i < args.length; i++) {
                    ctx.setVariable("p" + i, args[i]);
                }
                ctx.setVariable("args", args);
            }
            Expression exp = parser.parseExpression(expression);
            Object val = exp.getValue(ctx);
            return val == null ? "null" : String.valueOf(val);
        } catch (Exception e) {
            return defaultKey(args);
        }
    }

    private String defaultKey(Object[] args) {
        if (args == null || args.length == 0) {
            return "all";
        }
        StringBuilder sb = new StringBuilder();
        for (Object a : args) {
            sb.append(a == null ? "null" : a.toString()).append("|");
        }
        return sb.toString();
    }
}
