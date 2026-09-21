package com.cim.auth;

import com.cim.auth.datapermission.DataPermissionContext;
import com.cim.auth.datapermission.DataPermissionContextHolder;
import com.cim.auth.datapermission.DataPermissionSpec;
import com.cim.auth.datapermission.Scope;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** T3.6：数据权限谓词构造（零拼接）。 */
class DataPermissionSpecTest {

    @SuppressWarnings("unchecked")
    @Test
    void noContext_returnsConjunction() {
        DataPermissionContextHolder.clear();
        Root<Object> root = mock(Root.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        Predicate conjunction = mock(Predicate.class);
        when(cb.conjunction()).thenReturn(conjunction);

        Predicate result = DataPermissionSpec.toPredicate(root, cb);

        org.junit.jupiter.api.Assertions.assertNotNull(result);
        verify(cb).conjunction();
        verify(cb, never()).equal(any(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void tenantScope_filtersByTenantId() {
        DataPermissionContext ctx = new DataPermissionContext();
        ctx.scope = Scope.TENANT;
        ctx.tenantId = "t9";
        DataPermissionContextHolder.set(ctx);
        try {
            Root<Object> root = mock(Root.class);
            CriteriaBuilder cb = mock(CriteriaBuilder.class);
            CriteriaQuery<?> query = mock(CriteriaQuery.class);
            Predicate equal = mock(Predicate.class);
            when(cb.equal(any(), eq("t9"))).thenReturn(equal);

            Predicate result = DataPermissionSpec.toPredicate(root, cb);

            org.junit.jupiter.api.Assertions.assertNotNull(result);
            verify(cb).equal(root.get("tenantId"), "t9");
        } finally {
            DataPermissionContextHolder.clear();
        }
    }
}
