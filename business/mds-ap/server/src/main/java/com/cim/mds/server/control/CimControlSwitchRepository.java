package com.cim.mds.server.control;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** 卡控开关仓储（V18）。 */
public interface CimControlSwitchRepository extends JpaRepository<CimControlSwitch, String> {

    Optional<CimControlSwitch> findByControlCodeAndDeletedFalse(String controlCode);

    boolean existsByControlCodeAndDeletedFalse(String controlCode);

    /** 按域批量（消费方 TTL 缓存拉取口径）。 */
    List<CimControlSwitch> findByDomainCodeAndDeletedFalseOrderByControlCodeAsc(String domainCode);

    /** 全量（跨域管理口径）。 */
    List<CimControlSwitch> findByDeletedFalseOrderByDomainCodeAscControlCodeAsc();
}
