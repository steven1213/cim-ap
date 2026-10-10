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
 * <p><b>租户过滤（本模块是平台唯一的 {@code @FilterDef} 声明点 —— ADR-12）</b>：
 * Hibernate 要求 {@code @FilterDef} 的名字<b>在每个持久化单元内唯一</b>，同名重复声明会直接
 * 抛 {@code AnnotationException: Multiple '@FilterDef' annotations define a filter named 'cimTenantFilter'}
 * （Hibernate 6.2+ 起，无开关可关；见 HHH-16581 / HHH-16803）。因此平台约定：
 * <b>只在 {@code com.cim.system} 的 {@code package-info} 声明一次</b>，<b>任何其他模块
 * （starter 如 {@code cim-i18n-starter}、业务 ap）都不得再声明</b>，各租户化实体仅标
 * {@code @Filter(name="cimTenantFilter", condition="tenant_id = :tenantId")} 引用它。</p>
 *
 * <p>选 {@code com.cim.system} 作为唯一声明点的原因：它是<b>平台必备的系统域模块</b>（宿主 ap
 * 与 {@code cim-bootstrap} 都会引入），且已由 {@code CimSystemConfiguration} 声明
 * {@code @EntityScan("com.cim.system")} —— 既保证「只要有租户化实体就一定有该声明」，又
 * <b>不</b>需要让 starter 去声明 {@code @EntityScan}（那会把宿主的实体扫描列表整体顶掉，
 * 见 Spring 仅在 {@code EntityScanPackages} 为空时才回退到宿主自身包的语义）。</p>
 */
@FilterDef(name = "cimTenantFilter",
        parameters = @ParamDef(name = "tenantId", type = String.class))
package com.cim.system;

import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
