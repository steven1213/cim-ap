/**
 * cim-i18n-starter 国际化模块（design.md §2.7 / README §7）。
 *
 * <p><b>职责</b>：全量 UI/校验/异常文案入库（{@code sys_locale} + {@code sys_i18n}），
 * 由数据库版 {@code MessageSource} 解析，四级兜底、默认 {@code zh-CN}，支持热更新。</p>
 *
 * <p><b>租户过滤（本模块刻意 <u>不</u> 声明 {@code @FilterDef} —— ADR-12）</b>：Hibernate 要求
 * {@code @FilterDef} 的名字<b>在每个持久化单元内唯一</b>，同名重复声明会直接抛
 * {@code AnnotationException: Multiple '@FilterDef' annotations define a filter named 'cimTenantFilter'}
 * （Hibernate 6.2+，无开关可关；见 HHH-16581 / HHH-16803）。平台因此把
 * {@code cimTenantFilter} 的<b>唯一声明点</b>放在 {@code com.cim.system.package-info}
 * （平台必备的系统域模块）。本模块的 {@code SysLocale}/{@code SysI18n} 仅标
 * {@code @Filter(name="cimTenantFilter", condition="tenant_id = :tenantId")} 引用它。</p>
 *
 * <p>⚠️ <b>接入前提</b>：任何含 i18n 实体的应用，其持久化单元里必须存在该
 * {@code @FilterDef} —— 平台宿主由 {@code cim-system} 提供（IAM、{@code cim-bootstrap} 均满足）。
 * 若某宿主<b>只</b>引 i18n 而不引 {@code cim-system}，需自行声明一次；否则{@code @Filter}因带显式
 * {@code condition} 在启动期不会报错，但 {@code session.enableFilter("cimTenantFilter")}
 * （{@code TenantFilterApplier}）在开启租户上下文时会失败，且多租户隔离失效。</p>
 */
package com.cim.i18n;
