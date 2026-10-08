package com.cim.cache.support;

import com.cim.cache.multi.MultiLevelCache;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;

/**
 * {@link CacheableCim} 切面：拦截标注方法，经 {@link MultiLevelCache} 读取/回填缓存。
 *
 * <p>命中顺序：L1 → L2 → 回源（互斥重建，防击穿）。查无结果时由 {@link MultiLevelCache} 写入空值占位（防穿透）。</p>
 */
@Slf4j
@Aspect
public class CacheableCimAspect {

    private final MultiLevelCache multiLevelCache;
    private final CacheKeyGenerator keyGenerator;

    public CacheableCimAspect(MultiLevelCache multiLevelCache, CacheKeyGenerator keyGenerator) {
        this.multiLevelCache = multiLevelCache;
        this.keyGenerator = keyGenerator;
    }

    @Around("@annotation(com.cim.cache.support.CacheableCim)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature ms = (MethodSignature) pjp.getSignature();
        Method method = ms.getMethod();
        CacheableCim ann = method.getAnnotation(CacheableCim.class);
        String key = keyGenerator.generate(ann.value(), ann.key(), pjp.getArgs());
        Class<?> returnType = ms.getReturnType();
        if (Void.TYPE.equals(returnType) || Void.class.equals(returnType)) {
            return pjp.proceed();
        }
        return multiLevelCache.get(key, returnType, () -> {
            try {
                return pjp.proceed();
            } catch (Throwable t) {
                throw new CacheAspectException(t);
            }
        });
    }

    /** 切面内回源异常的未受检包装，便于向上抛出原始原因。 */
    public static final class CacheAspectException extends RuntimeException {
        public CacheAspectException(Throwable cause) {
            super(cause);
        }
    }
}
