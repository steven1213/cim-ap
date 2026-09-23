/**
 * 测试实体包：集中声明租户过滤器的 {@code @FilterDef}（过滤器定义须全局唯一，
 * 故放在 package-info 而非各实体上；实体仅用 {@code @Filter} 引用，见 README §10）。
 */
@FilterDef(name = "cimTenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))
package com.cim.jpa.it;

import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
