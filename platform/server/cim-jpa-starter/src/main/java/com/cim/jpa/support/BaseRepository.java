package com.cim.jpa.support;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * 通用仓储基类：JPA CRUD + 动态查询（Specification）。
 *
 * <p>业务只需 {@code public interface EquipmentDefRepository extends BaseRepository<EquipmentDef, String> {}}</p>
 *
 * <p>因需 Spring Data JPA 依赖，本接口落地在 {@code cim-jpa-starter} 而非
 * {@code cim-spring-support}（见 design.md §2.2）。</p>
 *
 * @param <T>  实体类型
 * @param <ID> 主键类型
 */
@NoRepositoryBean
public interface BaseRepository<T, ID> extends JpaRepository<T, ID>, JpaSpecificationExecutor<T> {
}
