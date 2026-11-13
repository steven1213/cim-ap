package com.cim.mds.server.equipment;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 设备能力仓储。 */
public interface CimCapabilityRepository extends JpaRepository<CimCapability, String> {

    Optional<CimCapability> findByCapabilityCodeAndDeletedFalse(String capabilityCode);

    boolean existsByCapabilityCodeAndDeletedFalse(String capabilityCode);

    /** 列表 + 关键字（编码/名称模糊，{@code kw} 可空）。 */
    @Query("""
            select c from CimCapability c
             where c.deleted = false
               and (:kw is null
                    or lower(c.capabilityCode) like lower(concat('%', :kw, '%'))
                    or lower(c.capabilityName) like lower(concat('%', :kw, '%')))
             order by c.capabilityCode asc
            """)
    List<CimCapability> search(@Param("kw") String keyword);
}
