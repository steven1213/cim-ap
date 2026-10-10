package com.cim.system.permission;

import com.cim.system.support.EnableStatus;
import com.cim.system.support.PermissionCodes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 平台权限目录种子（幂等）：把 {@link PermissionCodes} 里的权限码写入 {@code sys_permission}。
 *
 * <p><b>为什么必须种子</b>：超管（{@code sys_role.is_super=true}）在
 * {@code DbLocalAuthorityLoader} 里<b>短路展开为「库内全部启用权限码」</b>。若权限目录为空，
 * 超管的 authorities 里就<b>不含</b> {@code sys:menu:list} 之类的码——
 * 而 {@code @PreAuthorize("hasAuthority('sys:menu:list')")} 走的是 Spring Security 的
 * <b>精确串匹配</b>（不查 {@code PermissionEvaluator}），于是「超级管理员被自己的接口拒绝」。
 * 本类把「模块声明的权限码」落成目录行，从根上消除这个错配。</p>
 *
 * <p>由 {@code CimSystemConfiguration} 在 {@code cim.system.seed-permissions=true}（默认）时装配；
 * 幂等（按 {@code code} 跳过已存在行），不覆盖管理端的改名/停用。</p>
 */
@Slf4j
public class SysPermissionSeeder implements ApplicationRunner {

    private final SysPermissionRepository permissionRepository;

    public SysPermissionSeeder(SysPermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    /** 权限码 → 面向管理员的名称（授权界面展示用）。 */
    private static Map<String, String> catalog() {
        Map<String, String> c = new LinkedHashMap<>();
        // 用户
        c.put(PermissionCodes.USER_LIST, "用户查询");
        c.put(PermissionCodes.USER_SAVE, "用户保存");
        c.put(PermissionCodes.USER_REMOVE, "用户删除");
        c.put(PermissionCodes.USER_GRANT, "用户授权");
        // 角色
        c.put(PermissionCodes.ROLE_LIST, "角色查询");
        c.put(PermissionCodes.ROLE_SAVE, "角色保存");
        c.put(PermissionCodes.ROLE_REMOVE, "角色删除");
        c.put(PermissionCodes.ROLE_GRANT, "角色授权");
        // 菜单
        c.put(PermissionCodes.MENU_LIST, "菜单查询");
        c.put(PermissionCodes.MENU_SAVE, "菜单保存");
        c.put(PermissionCodes.MENU_REMOVE, "菜单删除");
        // 权限
        c.put(PermissionCodes.PERMISSION_LIST, "权限查询");
        c.put(PermissionCodes.PERMISSION_SAVE, "权限保存");
        c.put(PermissionCodes.PERMISSION_REMOVE, "权限删除");
        // 字典
        c.put(PermissionCodes.DICT_LIST, "字典查询");
        c.put(PermissionCodes.DICT_SAVE, "字典保存");
        c.put(PermissionCodes.DICT_REMOVE, "字典删除");
        // 参数
        c.put(PermissionCodes.CONFIG_LIST, "参数查询");
        c.put(PermissionCodes.CONFIG_SAVE, "参数保存");
        c.put(PermissionCodes.CONFIG_REMOVE, "参数删除");
        // 日志
        c.put(PermissionCodes.LOG_LIST, "日志查询");
        return c;
    }

    @Override
    public void run(ApplicationArguments args) {
        int written = 0;
        for (Map.Entry<String, String> e : catalog().entrySet()) {
            if (permissionRepository.findFirstByCodeOrderByCreateTimeAsc(e.getKey()).isPresent()) {
                continue;
            }
            SysPermission permission = new SysPermission();
            permission.applyCode(e.getKey());
            permission.setName(e.getValue());
            permission.setStatus(EnableStatus.ENABLED);
            permissionRepository.save(permission);
            written++;
        }
        if (written > 0) {
            log.info("[cim-system] 权限目录种子：写入 {} 条平台权限码", written);
        }
    }
}
