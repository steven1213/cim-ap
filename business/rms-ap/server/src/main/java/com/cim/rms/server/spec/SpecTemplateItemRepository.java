package com.cim.rms.server.spec;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 规格模板条目仓储。 */
public interface SpecTemplateItemRepository extends JpaRepository<SpecTemplateItem, String> {

    List<SpecTemplateItem> findByTemplateIdAndDeletedFalseOrderBySortNoAsc(String templateId);

    void deleteByTemplateId(String templateId);
}
