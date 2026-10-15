package com.cim.rms.server.template;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BodyTemplateItemRepository extends JpaRepository<BodyTemplateItem, String> {

    List<BodyTemplateItem> findByTemplateIdAndDeletedFalseOrderBySortNoAsc(String templateId);
}
