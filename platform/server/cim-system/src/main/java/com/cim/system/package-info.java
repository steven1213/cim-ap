/**
 * cim-system 系统域模块（design.md §2.10）。
 *
 * <p><b>职责</b>：本 ap 内部权限的<b>数据落地</b>——用户（授权档案）、角色、菜单、权限、
 * 字典、参数、操作/登录日志，并通过 {@code com.cim.system.rbac.DbLocalAuthorityLoader}
 * 向 {@code cim-auth-starter} 提供 {@code LocalAuthorityLoader} 实现。</p>
 *
 * <p><b>边界（勿与 IAM 混淆）</b>：本模块<b>不做</b>统一登录、口令校验、令牌签发与跨 ap 准入
 * （那些属 {@code business/iam-ap}）。{@code sys_user} 是「把 AD/IAM 身份授权到本 ap 角色」的
 * <b>授权档案</b>，因此 <b>不含口令字段</b>。</p>
 *
 * <p><b>租户过滤</b>：按平台约定（见 {@code cim-jpa-starter} 的 {@code TenantFilterApplier}），
 * {@code @FilterDef} 每应用声明一次、各实体仅标 {@code @Filter}。此处声明的
 * {@code cimTenantFilter} 与平台同名同参，重复声明等价（幂等）。</p>
 */
@FilterDef(name = "cimTenantFilter",
        parameters = @ParamDef(name = "tenantId", type = String.class))
package com.cim.system;

import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
