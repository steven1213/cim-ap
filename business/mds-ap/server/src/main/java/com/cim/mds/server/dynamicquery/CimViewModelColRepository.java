package com.cim.mds.server.dynamicquery;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CimViewModelColRepository extends JpaRepository<CimViewModelCol, String> {

    List<CimViewModelCol> findByViewModelIdAndDeletedFalseOrderBySortNoAsc(String viewModelId);

    void deleteByViewModelId(String viewModelId);
}
