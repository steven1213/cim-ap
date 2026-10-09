package com.cim.iam.server.watermark;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 目录水位（identity-directory.md §3.5）。
 *
 * <p>与既有 {@code token_version} 同构的单调递增版本号：目录一旦变更即 +1。
 * 业务 ap 只需轻量比对水位（{@code GET /api/v1/directory/watermark}），变了才全量拉取，
 * 避免轮询大表。离职传播亦借它作缓存失效信号。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "directory_watermark", uniqueConstraints = {
        @UniqueConstraint(name = "uk_directory_watermark_scope", columnNames = "scope")
})
public class DirectoryWatermark {

    @Id
    @Column(name = "id", length = 64, nullable = false, updatable = false)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", length = 32, nullable = false)
    private WatermarkScope scope;

    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
