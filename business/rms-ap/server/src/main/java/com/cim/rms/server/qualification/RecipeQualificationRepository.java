package com.cim.rms.server.qualification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 配方-机台资格仓储。 */
public interface RecipeQualificationRepository extends JpaRepository<RecipeQualification, String> {

    List<RecipeQualification> findByDeletedFalseOrderByCreateTimeDesc();

    List<RecipeQualification> findByRecipeIdAndDeletedFalse(String recipeId);

    List<RecipeQualification> findByDeviceIdAndDeletedFalse(String deviceId);

    List<RecipeQualification> findByStatusAndDeletedFalse(String status);

    long countByStatusAndDeletedFalse(String status);
}
