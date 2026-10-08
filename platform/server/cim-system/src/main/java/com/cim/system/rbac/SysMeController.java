package com.cim.system.rbac;

import com.cim.core.port.CurrentUserPort;
import com.cim.spring.support.web.Result;
import com.cim.system.autoconfigure.CimSystemProperties;
import com.cim.system.menu.MenuNode;
import com.cim.system.user.SysUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * 「当前登录人」自助接口——前端路由守卫与按钮显隐的<b>权威数据源</b>（README §21.1 第 4 点：
 * 「前端路由守卫与后端过滤共用同一份权限」）。
 *
 * <p>权限集直接回显已注入 {@code SecurityContext} 的 authorities（即 RBAC 解析结果），
 * <b>不重新查库</b>：保证「前端看到的」与「后端鉴权用的」严格是同一份，
 * 不会出现前端有按钮、后端拒绝的错配。</p>
 *
 * <p>身份 → 本地档案的映射走 {@link LocalUserResolver}，与认证期同一个解析器。</p>
 */
@RestController
@RequestMapping("/sys/me")
@PreAuthorize("isAuthenticated()")
public class SysMeController {

    private final CurrentUserPort currentUserPort;
    private final LocalUserResolver userResolver;
    private final SysRbacService rbacService;
    private final CimSystemProperties properties;

    public SysMeController(CurrentUserPort currentUserPort,
                           LocalUserResolver userResolver,
                           SysRbacService rbacService,
                           CimSystemProperties properties) {
        this.currentUserPort = currentUserPort;
        this.userResolver = userResolver;
        this.rbacService = rbacService;
        this.properties = properties;
    }

    /** 当前用户在本 ap 的权限码集（含超管标记）。 */
    @GetMapping("/permissions")
    public Result<Set<String>> permissions() {
        return Result.ok(currentUserPort.authorities());
    }

    /** 当前用户可见的菜单树（受 {@code cim.system.menu.include-buttons} 控制是否含按钮节点）。 */
    @GetMapping("/menus")
    public Result<List<MenuNode>> menus() {
        return userResolver.resolve(currentUserPort.userId(), currentUserPort.username())
                .map(user -> Result.ok(rbacService.menuTree(user.getId(),
                        properties.getMenu().isIncludeButtons())))
                .orElseGet(() -> Result.ok(List.of()));
    }

    /** 当前用户的本地授权档案概要（未在本 ap 建档时 {@code provisioned=false}）。 */
    @GetMapping("/profile")
    public Result<MeProfile> profile() {
        return userResolver.resolve(currentUserPort.userId(), currentUserPort.username())
                .map(user -> Result.ok(new MeProfile(true, user.getId(), user.getUsername(),
                        user.getDisplayName(), user.getLang(), user.getExternalId())))
                .orElseGet(() -> Result.ok(new MeProfile(false, currentUserPort.userId(),
                        currentUserPort.username(), null, null, null)));
    }

    /**
     * 当前用户档案概要。
     *
     * @param provisioned 是否已在本 ap 建档（未建档时其余字段回退令牌身份）
     * @param localUserId {@code sys_user.id}（未建档为 null）
     * @param username    账号
     * @param displayName 显示名
     * @param lang        语言偏好
     * @param externalId  外部身份标识
     */
    public record MeProfile(boolean provisioned, String localUserId, String username,
                            String displayName, String lang, String externalId) {
    }
}
