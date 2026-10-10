package com.cim.i18n.model;

import com.cim.jpa.support.BaseRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 译文仓储。 */
public interface SysI18nRepository extends BaseRepository<SysI18n, String> {

    /** 某语言的全部译文（**注意**：由 {@code I18nCache} 一次性加载后缓存）。 */
    List<SysI18n> findByLocaleCode(String localeCode);

    /** 精确查某一语言的某个键（唯一键）。 */
    Optional<SysI18n> findByLocaleCodeAndCode(String localeCode, String code);

    /** 某键在各语言下的译文（管理端「键视图」）。 */
    List<SysI18n> findByCodeOrderByLocaleCodeAsc(String code);

    /** 按模块筛选。 */
    List<SysI18n> findByLocaleCodeAndModule(String localeCode, String module);

    /** 最大事件时间（与行数一起构成前端可见的译文版本号）。 */
    @Query("select max(i.eventTime) from SysI18n i")
    LocalDateTime maxEventTime();
}
