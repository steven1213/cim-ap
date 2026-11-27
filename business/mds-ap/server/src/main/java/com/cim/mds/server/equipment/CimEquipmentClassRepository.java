package com.cim.mds.server.equipment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 机型模板（EquipmentClass）仓库。
 *
 * <p>检索范围含已停用（{@code is_enabled=false}）——停用模板的存量机台仍需回显；
 * 已软删的除外。</p>
 */
public interface CimEquipmentClassRepository extends JpaRepository<CimEquipmentClass, String> {

    Optional<CimEquipmentClass> findByClassCodeAndDeletedFalse(String classCode);

    boolean existsByClassCodeAndDeletedFalse(String classCode);

    boolean existsByClassCodeAndDeletedFalseAndEnabledTrue(String classCode);

    @Query("""
            select t from CimEquipmentClass t
            where t.deleted = false
              and (:keyword is null or lower(t.classCode) like lower(concat('%', :keyword, '%'))
                   or lower(t.className) like lower(concat('%', :keyword, '%')))
            order by t.classCode
            """)
    List<CimEquipmentClass> search(@Param("keyword") String keyword);
}
