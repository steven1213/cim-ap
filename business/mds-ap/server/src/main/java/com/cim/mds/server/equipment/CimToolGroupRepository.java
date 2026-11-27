package com.cim.mds.server.equipment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** 机台组仓库。 */
public interface CimToolGroupRepository extends JpaRepository<CimToolGroup, String> {

    Optional<CimToolGroup> findByGroupCodeAndDeletedFalse(String groupCode);

    @Query("""
            select g from CimToolGroup g
            where g.deleted = false
              and (:keyword is null or lower(g.groupCode) like lower(concat('%', :keyword, '%'))
                   or lower(g.groupName) like lower(concat('%', :keyword, '%')))
              and (:classCode is null or g.classCode = :classCode)
            order by g.groupCode
            """)
    List<CimToolGroup> search(@Param("keyword") String keyword, @Param("classCode") String classCode);
}
