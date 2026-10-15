package com.cim.rms.server.effect;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RmsChangeEffectRepository extends JpaRepository<RmsChangeEffect, String> {

    List<RmsChangeEffect> findByRecipeIdAndDeletedFalseOrderByActivatedAtDesc(String recipeId);
}
