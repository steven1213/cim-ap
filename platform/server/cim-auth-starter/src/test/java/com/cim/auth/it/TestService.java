package com.cim.auth.it;

import com.cim.auth.datapermission.DataPermission;
import com.cim.auth.datapermission.DataPermissionContextHolder;
import com.cim.auth.datapermission.Scope;

import org.springframework.stereotype.Service;

/** 验证数据权限切面：方法执行时作用域上下文应已就绪。 */
@Service
public class TestService {

    @DataPermission(Scope.TENANT)
    public String currentTenant() {
        var ctx = DataPermissionContextHolder.get();
        return ctx == null ? null : ctx.tenantId;
    }
}
