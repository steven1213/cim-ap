package com.cim.mds.server.dynamicquery;

import com.cim.core.model.BaseDefData;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 查询声明参数（V39，全量替换维护）——Query 的输入契约。
 *
 * <p>param_type（mds.dq_param_type 字典）：STRING/NUMBER/DATE/DICT（DICT 时
 * dict_code 必填，前端渲染下拉）。sql_text 中的 {@code :param_key} 占位必须与
 * 本表声明完全一致（校验器双向守门：未声明被引用、声明未使用都拒绝保存）。</p>
 */
@Entity
@Table(name = "cim_query_param")
@Getter
@Setter
public class CimQueryParam extends BaseDefData {

    @Column(name = "query_id", length = 64, nullable = false, updatable = false)
    private String queryId;

    /** 参数键（sql_text 中 :param_key 占位名）。 */
    @Column(name = "param_key", length = 64, nullable = false)
    private String paramKey;

    @Column(name = "param_label", length = 128, nullable = false)
    private String paramLabel;

    /** STRING / NUMBER / DATE / DICT。 */
    @Column(name = "param_type", length = 16, nullable = false)
    private String paramType = "STRING";

    @Column(name = "is_required", nullable = false)
    private Boolean required = Boolean.FALSE;

    /** DICT 型参数的下拉字典码（如 mds.consumable_type）。 */
    @Column(name = "dict_code", length = 64)
    private String dictCode;

    @Column(name = "sort_no", nullable = false)
    private Integer sortNo = 0;
}
