package com.cim.system.user;

import com.cim.jpa.support.BaseRepository;

import java.util.Optional;

/**
 * 用户仓储。
 *
 * <p>查询语义说明：{@code username} / {@code externalId} 在「同一租户 + 未删除」内唯一
 * （见实体唯一约束），但仍用 {@code findFirst...} 而非 {@code findBy}，避免多租户场景下
 * 无租户上下文时命中多行抛出 {@code NonUniqueResultException}。</p>
 */
public interface SysUserRepository extends BaseRepository<SysUser, String> {

    /** 按外部身份标识查找（对齐 IAM 令牌 {@code sub}）。 */
    Optional<SysUser> findFirstByExternalIdOrderByCreateTimeAsc(String externalId);

    /** 按登录名查找（对齐 IAM 令牌 {@code username}）。 */
    Optional<SysUser> findFirstByUsernameOrderByCreateTimeAsc(String username);

    /** 登录名是否已存在。 */
    boolean existsByUsername(String username);
}
