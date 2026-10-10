package com.cim.rms.server.recipe;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 配方版本（Req 13/14/42；不可变存档语义——Body 一经保存不建议改，改动即新版本）。
 *
 * <p>Body 字段（{@code bodyFormat}/{@code bodyBase64}/{@code bodyHash}）对应接口 IF-E1/E2 的
 * 传输三元组；参数快照（{@code paramSnapshot}）为后续机型格式适配器（Req.md §1.2 P2）与
 * 参数级比对（FR-C4）的结构化落点，W1 允许为空。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "recipe_version", uniqueConstraints = {
        @UniqueConstraint(name = "uk_recipe_version_no", columnNames = {"recipe_id", "version_no"})
})
public class RecipeVersion extends BaseDefData {

    /** 所属配方（{@link Recipe} id）。 */
    @Column(name = "recipe_id", length = 64, nullable = false)
    private String recipeId;

    /** 版本号（配方内单调递增，从 1 开始）。 */
    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private RecipeVersionStatus status = RecipeVersionStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "body_format", length = 16, nullable = false)
    private BodyFormat bodyFormat = BodyFormat.TEXT;

    /** Body 内容（二进制以 Base64 存档；大对象存储方案见已拍板 D4，W1 先行库内 CLOB）。 */
    @Lob
    @Column(name = "body_base64")
    private String bodyBase64;

    /** Body SHA-256（完整性校验，IF-COM 完整性红线）。 */
    @Column(name = "body_hash", length = 64)
    private String bodyHash;

    /** 参数快照（JSON 结构化，可空；W2 机型适配器产出）。 */
    @Lob
    @Column(name = "param_snapshot")
    private String paramSnapshot;

    /** 变更摘要（签核/差异可视的依据，FR-L4）。 */
    @Column(name = "change_summary", length = 512)
    private String changeSummary;

    /** 血缘：复制/回退来源版本（FR-F4 / FR-L3），原始新建为 {@code null}。 */
    @Column(name = "source_version_id", length = 64)
    private String sourceVersionId;

    /** 生效时间（激活时记录）。 */
    @Column(name = "activated_at")
    private Instant activatedAt;

    /** 生效人（W1 取操作上下文，W2 接签核人）。 */
    @Column(name = "activated_by", length = 64)
    private String activatedBy;
}
