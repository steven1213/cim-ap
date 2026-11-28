package com.cim.mds.server.dynamicquery;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CimViewModelRepository extends JpaRepository<CimViewModel, String> {

    Optional<CimViewModel> findByViewCodeAndDeletedFalse(String viewCode);

    List<CimViewModel> findByDeletedFalseOrderByViewCodeAsc();

    boolean existsByViewCodeAndDeletedFalse(String viewCode);

    List<CimViewModel> findByQueryCodeAndDeletedFalse(String queryCode);
}
