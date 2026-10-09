package com.cim.iam.server.watermark;

import com.cim.core.port.IdGenerator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 目录水位存储与递增（identity-directory.md §3.5）。
 *
 * <p>语义与 {@link com.cim.iam.server.token.TokenVersionService} 完全同构：
 * {@link #current(WatermarkScope)} 首次访问建记录并返回 1；{@link #bump(WatermarkScope)} 单调 +1。
 * 目录变更（档案/归属/组织/组织授予）后立即 bump，业务 ap 比对水位决定是否重拉。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DirectoryWatermarkService {

    private final DirectoryWatermarkRepository repository;
    private final IdGenerator idGenerator;

    /** 当前水位（首次访问建记录并返回 1）。 */
    @Transactional
    public long current(WatermarkScope scope) {
        return repository.findByScope(scope)
                .map(DirectoryWatermark::getVersion)
                .orElseGet(() -> {
                    DirectoryWatermark w = new DirectoryWatermark();
                    w.setId(idGenerator.nextId());
                    w.setScope(scope);
                    w.setVersion(1L);
                    w.setUpdatedAt(Instant.now());
                    repository.save(w);
                    return 1L;
                });
    }

    /** 水位 +1，返回新值。 */
    @Transactional
    public long bump(WatermarkScope scope) {
        DirectoryWatermark w = repository.findByScope(scope).orElseGet(() -> {
            DirectoryWatermark t = new DirectoryWatermark();
            t.setId(idGenerator.nextId());
            t.setScope(scope);
            t.setVersion(0L);
            return t;
        });
        w.setVersion(w.getVersion() + 1);
        w.setUpdatedAt(Instant.now());
        repository.save(w);
        log.debug("[directory-watermark] bump scope={} -> {}", scope, w.getVersion());
        return w.getVersion();
    }
}
