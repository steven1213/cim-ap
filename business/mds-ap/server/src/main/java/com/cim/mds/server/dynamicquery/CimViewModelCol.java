package com.cim.mds.server.dynamicquery;

import com.cim.core.model.BaseDefData;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 视图模型列（V39，全量替换维护）——UI 渲染契约的列元数据。
 *
 * <p>col_key 必须存在于所属 Query 的结果集（保存时对 dry-run 元数据硬校验）。
 * 标题两轨：title_key（i18n 键优先）为空回落 col_title（字面量兜底——在线
 * 新加的列无法即时配翻译键）。format_type（mds.dq_format_type 字典）：
 * NONE/DATE/DATETIME/DICT/NUMBER；DICT 时 format_arg=业务字典码。</p>
 */
@Entity
@Table(name = "cim_view_model_col")
@Getter
@Setter
public class CimViewModelCol extends BaseDefData {

    @Column(name = "view_model_id", length = 64, nullable = false, updatable = false)
    private String viewModelId;

    /** 结果集列名（dry-run 元数据校验通过才允许保存）。 */
    @Column(name = "col_key", length = 64, nullable = false)
    private String colKey;

    /** 字面量标题（title_key 为空时兜底展示）。 */
    @Column(name = "col_title", length = 128)
    private String colTitle;

    /** i18n 标题键（优先于字面量）。 */
    @Column(name = "title_key", length = 128)
    private String titleKey;

    /** 列宽 px（空=组件默认弹性）。 */
    @Column(name = "width")
    private Integer width;

    /** LEFT / CENTER / RIGHT。 */
    @Column(name = "align", length = 8, nullable = false)
    private String align = "LEFT";

    @Column(name = "is_visible", nullable = false)
    private Boolean visible = Boolean.TRUE;

    /** NONE / DATE / DATETIME / DICT / NUMBER。 */
    @Column(name = "format_type", length = 16, nullable = false)
    private String formatType = "NONE";

    /** DICT=字典码 / DATE=格式串 / NUMBER=小数位（可空）。 */
    @Column(name = "format_arg", length = 64)
    private String formatArg;

    @Column(name = "sort_no", nullable = false)
    private Integer sortNo = 0;
}
