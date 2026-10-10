package com.cim.rms.server.recipe;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 配方主档仓储。 */
public interface RecipeRepository extends JpaRepository<Recipe, String> {

    Optional<Recipe> findByCodeAndDeletedFalse(String code);

    /** 通配符查询（FR-F2）：keyword 已由服务层完成 {@code *→%} 转换与 LIKE 转义。 */
    @Query("""
            select r from Recipe r
            where r.deleted = false
              and (:keyword is null or lower(r.code) like lower(:keyword) escape '\\' or lower(r.name) like lower(:keyword) escape '\\')
              and (:deviceTypeId is null or r.deviceTypeId = :deviceTypeId)
              and (:areaId is null or r.areaId = :areaId)
              and (:golden is null or r.golden = :golden)
            """)
    List<Recipe> search(@Param("keyword") String keyword,
                        @Param("deviceTypeId") String deviceTypeId,
                        @Param("areaId") String areaId,
                        @Param("golden") Boolean golden,
                        Sort sort);

    List<Recipe> findByDeviceTypeIdAndDeletedFalse(String deviceTypeId);

    List<Recipe> findByAreaIdAndDeletedFalse(String areaId);
}
