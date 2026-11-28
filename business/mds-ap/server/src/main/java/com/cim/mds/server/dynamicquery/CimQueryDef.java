package com.cim.mds.server.dynamicquery;

import com.cim.mds.server.site.EditLockDefData;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 动态查询定义（V39）——「取数契约」：sql_text + 声明参数 + 分页上限。
 *
 * <p>用户拍板口径：允许保存 SQL 文本，但落库必须通过 {@link DynamicQuerySqlValidator}
 * 三层验证的前两层（语法 SELECT-only + 参数一致），第三层真跑 dry-run 由
 * {@link DynamicQueryExecutor} 在保存时执行（表/列真实存在才放行）。
 * validated_at 记录最近一次通过验证的时间。</p>
 *
 * <p>query_code 全局唯一、建后不可改；ViewModel 按 query_code（非 id）引用，
 * 跨环境迁移幂等。运行时执行强制只读连接 + 超时 + 行数封顶（page_size_max）。</p>
 */
@Entity
@Table(name = "cim_query_def")
@Getter
@Setter
public class CimQueryDef extends EditLockDefData {

    /** 查询编码（全局唯一、建后不可改；ViewModel 按 code 引用）。 */
    @Column(name = "query_code", length = 64, nullable = false, updatable = false)
    private String queryCode;

    @Column(name = "query_name", length = 128, nullable = false)
    private String queryName;

    /** 查询 SQL 文本（仅 SELECT；保存前三层验证）。 */
    @Column(name = "sql_text", columnDefinition = "CLOB", nullable = false)
    private String sqlText;

    /** 单页行数上限（运行时分页取 min(请求值, 本值)）。 */
    @Column(name = "page_size_max", nullable = false)
    private Integer pageSizeMax = 500;

    @Column(name = "is_enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;

    /** 最近一次三层验证通过时间。 */
    @Column(name = "validated_at")
    private LocalDateTime validatedAt;
}
