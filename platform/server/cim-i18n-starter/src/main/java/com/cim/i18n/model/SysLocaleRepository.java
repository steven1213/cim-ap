package com.cim.i18n.model;

import com.cim.jpa.support.BaseRepository;

import java.util.List;
import java.util.Optional;

/** 语言目录仓储。 */
public interface SysLocaleRepository extends BaseRepository<SysLocale, String> {

    /** 按语言标签查（唯一键一部分）。 */
    Optional<SysLocale> findByCode(String code);

    /** 启用的语言（按排序号）。 */
    List<SysLocale> findByStatusOrderBySortNoAsc(LocaleStatus status);

    /** 全部语言（按排序号）。 */
    List<SysLocale> findAllByOrderBySortNoAsc();

    /** 默认兜底语言。 */
    Optional<SysLocale> findFirstByIsDefaultTrue();
}
