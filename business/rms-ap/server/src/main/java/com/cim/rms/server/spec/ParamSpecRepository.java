package com.cim.rms.server.spec;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 参数规格仓储。 */
public interface ParamSpecRepository extends JpaRepository<ParamSpec, String> {

    List<ParamSpec> findByRecipeIdAndDeletedFalseOrderByParamKeyAsc(String recipeId);

    Optional<ParamSpec> findByRecipeIdAndParamKeyAndDeletedFalse(String recipeId, String paramKey);

    long countByRecipeIdAndDeletedFalse(String recipeId);

    long countByDeletedFalse();
}
