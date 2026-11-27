package com.cim.mds.server.reticle;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 光罩（Reticle）仓储。 */
public interface CimReticleRepository extends JpaRepository<CimReticle, String> {

    Optional<CimReticle> findByReticleCodeAndDeletedFalse(String reticleCode);

    boolean existsByReticleCodeAndDeletedFalse(String reticleCode);

    /** 列表：按状态/存放设备过滤（可空）+ 关键字（编码/名称/层别模糊）。 */
    @Query("""
            select r from CimReticle r
             where r.deleted = false
               and (:status is null or r.status = :status)
               and (:equipmentId is null or r.homeEquipmentId = :equipmentId)
               and (:kw is null
                    or lower(r.reticleCode) like lower(concat('%', :kw, '%'))
                    or lower(r.reticleName) like lower(concat('%', :kw, '%'))
                    or lower(r.layer) like lower(concat('%', :kw, '%')))
             order by r.reticleCode asc
            """)
    List<CimReticle> search(@Param("status") String status,
                            @Param("equipmentId") String equipmentId,
                            @Param("kw") String keyword);
}
