package com.cim.mds.server.dynamicquery;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CimQueryDefRepository extends JpaRepository<CimQueryDef, String> {

    Optional<CimQueryDef> findByQueryCodeAndDeletedFalse(String queryCode);

    List<CimQueryDef> findByDeletedFalseOrderByQueryCodeAsc();

    boolean existsByQueryCodeAndDeletedFalse(String queryCode);
}
