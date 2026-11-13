package com.cim.mds.server.site;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Stage 仓储。 */
public interface CimStageRepository extends JpaRepository<CimStage, String> {

    Optional<CimStage> findByFabCodeAndBayCodeAndStageCodeAndDeletedFalse(
            String fabCode, String bayCode, String stageCode);

    /**
     * 下游引用计数：删 Bay 前的守门依据。
     * 将来设备落 Stage（{@code location_id}）后，此处需叠加设备计数或改由设备域校验。
     */
    long countByFabCodeAndBayCodeAndDeletedFalse(String fabCode, String bayCode);

    /** 列表 + 归属过滤 + 关键字（{@code fabCode}/{@code bayCode}/{@code kw} 皆可空）。 */
    @Query("""
            select s from CimStage s
             where s.deleted = false
               and (:fabCode is null or s.fabCode = :fabCode)
               and (:bayCode is null or s.bayCode = :bayCode)
               and (:kw is null
                    or lower(s.stageCode) like lower(concat('%', :kw, '%'))
                    or lower(s.stageName) like lower(concat('%', :kw, '%')))
             order by s.fabCode asc, s.bayCode asc, s.stageCode asc
            """)
    List<CimStage> search(@Param("fabCode") String fabCode, @Param("bayCode") String bayCode,
                          @Param("kw") String keyword);
}
