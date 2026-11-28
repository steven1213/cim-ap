package com.cim.mds.server.dynamicquery;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CimQueryParamRepository extends JpaRepository<CimQueryParam, String> {

    List<CimQueryParam> findByQueryIdAndDeletedFalseOrderBySortNoAsc(String queryId);

    void deleteByQueryId(String queryId);
}
