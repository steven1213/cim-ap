package com.cim.rms.server.recipe;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** 配方版本仓储。 */
public interface RecipeVersionRepository extends JpaRepository<RecipeVersion, String> {

    List<RecipeVersion> findByRecipeIdAndDeletedFalseOrderByVersionNoDesc(String recipeId);

    Optional<RecipeVersion> findByRecipeIdAndVersionNoAndDeletedFalse(String recipeId, int versionNo);

    Optional<RecipeVersion> findFirstByRecipeIdAndDeletedFalseOrderByVersionNoDesc(String recipeId);

    long countByRecipeIdAndDeletedFalse(String recipeId);
}
