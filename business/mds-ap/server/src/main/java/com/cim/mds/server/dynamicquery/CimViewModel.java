package com.cim.mds.server.dynamicquery;

import com.cim.mds.server.site.EditLockDefData;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 视图模型（V39）——「UI 渲染契约」：按 query_code 引用查询定义，
 * 明细列配置列显隐/顺序/宽度/对齐/格式化。列调整需求在线改本表，零发版。
 *
 * <p>perm_code 可选：非空时 meta/data/export 端点要求调用方持有该权限码
 * （MES 复用时按视图隔离授权）；为空回落 mds:dynamic:view 全局权限。
 * allow_export 管控导出端点。</p>
 */
@Entity
@Table(name = "cim_view_model")
@Getter
@Setter
public class CimViewModel extends EditLockDefData {

    /** 视图编码（全局唯一、建后不可改；前端路由 /dynamic-view/{viewCode}）。 */
    @Column(name = "view_code", length = 64, nullable = false, updatable = false)
    private String viewCode;

    @Column(name = "view_name", length = 128, nullable = false)
    private String viewName;

    /** 关联查询编码（Query 按 code 引用，不用 id）。 */
    @Column(name = "query_code", length = 64, nullable = false)
    private String queryCode;

    /** 访问权限码（可空=回落 mds:dynamic:view）。 */
    @Column(name = "perm_code", length = 64)
    private String permCode;

    @Column(name = "allow_export", nullable = false)
    private Boolean allowExport = Boolean.FALSE;

    /** 默认页大小（执行时仍受 query.page_size_max 封顶）。 */
    @Column(name = "page_size", nullable = false)
    private Integer pageSize = 20;

    @Column(name = "is_enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;
}
