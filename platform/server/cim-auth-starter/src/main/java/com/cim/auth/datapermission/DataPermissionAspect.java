package com.cim.auth.datapermission;

import com.cim.auth.principal.CimUserPrincipal;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 数据权限切面（design.md §2.4 / T3.6）。
 *
 * <p>在 {@link DataPermission} 标注的方法执行前，从 SecurityContext 取出当前操作人，
 * 写入 {@link DataPermissionContextHolder}；方法结束后清理。Repository 侧通过
 * {@link DataPermissionSpec} 读取并构造过滤条件，全过程零 SQL 拼接。</p>
 */
@Aspect
public class DataPermissionAspect {

    @Around("@annotation(dp)")
    public Object around(ProceedingJoinPoint pjp, DataPermission dp) throws Throwable {
        DataPermissionContext ctx = new DataPermissionContext();
        ctx.scope = dp.value();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CimUserPrincipal p) {
            ctx.userId = p.userId();
            ctx.tenantId = p.tenantId();
            ctx.isSuper = p.isSuper();
        }
        DataPermissionContextHolder.set(ctx);
        try {
            return pjp.proceed();
        } finally {
            DataPermissionContextHolder.clear();
        }
    }
}
