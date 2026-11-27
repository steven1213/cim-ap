package com.cim.mds.server.site;

import com.cim.auth.principal.CimUserPrincipal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * 位置主数据编辑锁的原子抢锁 / 释放（location-design.md §8.1）。
 *
 * <p>用 {@link JdbcTemplate} 对五表统一实现（表名白名单 {@link #TABLES}），不在五个仓储里
 * 各写一遍 @Modifying JPQL。批量 UPDATE 天然绕过 Hibernate {@code @Filter}（租户过滤），
 * 与现有服务层同口径——写路径本就不依赖 @Filter。</p>
 *
 * <p>抢锁条件 {@code (is_occupied = FALSE OR occupied_by = :user OR occupied_at < :expire)}
 * 一次 UPDATE 同时覆盖「空闲可抢 / 本人续锁 / 残留锁超时过期」三种情形；
 * 影响行数 = 0 即「被他人持有且未过期」。</p>
 */
@Component
@RequiredArgsConstructor
public class EditLockSupport {

    /** 类型 → 表名白名单（type 来自 URL，绝不能拼进 SQL，必须过这张表）。 */
    private static final Map<String, String> TABLES = Map.ofEntries(
            Map.entry("site", "cim_site"),
            Map.entry("fab", "cim_fab"),
            Map.entry("area", "cim_area"),
            Map.entry("areaGroup", "cim_area_group"),
            Map.entry("bay", "cim_bay"),
            Map.entry("stage", "cim_stage"),
            Map.entry("technology", "cim_technology"),
            Map.entry("product", "cim_product"),
            Map.entry("equipmentClass", "cim_equipment_class"),
            Map.entry("equipment", "cim_equipment"),
            Map.entry("toolGroup", "cim_tool_group"),
            Map.entry("capability", "cim_capability"),
            Map.entry("stockerBank", "cim_stocker_bank"),
            Map.entry("edcSet", "cim_edc_param_set"),
            Map.entry("edcReportTpl", "cim_edc_report_tpl"),
            Map.entry("edcPlan", "cim_edc_plan"));

    /** URL 上的位置层类型集合（供控制器校验）。 */
    public static final Set<String> TYPES = TABLES.keySet();

    private final JdbcTemplate jdbc;
    private final EditLockProperties props;

    /** 锁状态视图（{@code mine} 由调用方按当前用户判定，这里只给原始态）。 */
    public record LockState(boolean occupied, String occupiedBy, LocalDateTime occupiedAt) {
    }

    /** 当前编辑者（无认证态——测试/开发——回落 anonymous，保证锁语义闭环可测）。 */
    public static String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CimUserPrincipal p && p.username() != null) {
            return p.username();
        }
        return "anonymous";
    }

    /** 锁状态查询（不落写）。 */
    public LockState state(String type, String id) {
        String table = table(type);
        return jdbc.query(
                "SELECT is_occupied, occupied_by, occupied_at FROM " + table
                        + " WHERE id = ? AND deleted = FALSE",
                rs -> {
                    if (!rs.next()) {
                        return new LockState(false, null, null);
                    }
                    return new LockState(rs.getBoolean(1), rs.getString(2),
                            rs.getObject(3, LocalDateTime.class));
                }, id);
    }

    /**
     * 原子抢锁。成功返回 {@code claimed=true}（此时锁必为本持有者——本人续锁或抢到过期锁）；
     * 失败返回 {@code claimed=false} 并带当前占用者状态，供前端提示「谁正在编辑」。
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public ClaimResult claim(String type, String id, String user) {
        String table = table(type);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expire = now.minus(props.getEditLockTimeout());
        int updated = jdbc.update(
                "UPDATE " + table
                        + " SET is_occupied = TRUE, occupied_by = ?, occupied_at = ?"
                        + " WHERE id = ? AND deleted = FALSE"
                        + "   AND (is_occupied = FALSE OR occupied_by = ? OR occupied_at < ?)",
                user, now, id, user, expire);
        if (updated == 1) {
            return new ClaimResult(true, user, now);
        }
        LockState state = state(type, id);
        return new ClaimResult(false, state.occupiedBy(), state.occupiedAt());
    }

    /**
     * 释放锁。非 force 只清自己的锁（{@code occupied_by = user} 条件）；
     * force 供管理员强制解锁（仍要求具备该层 update 权限，由控制器把关）。
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public boolean release(String type, String id, String user, boolean force) {
        String table = table(type);
        String sql = "UPDATE " + table
                + " SET is_occupied = FALSE, occupied_by = NULL, occupied_at = NULL"
                + " WHERE id = ? AND deleted = FALSE";
        if (!force) {
            sql += " AND occupied_by = ?";
            return jdbc.update(sql, id, user) == 1;
        }
        return jdbc.update(sql, id) == 1;
    }

    private static String table(String type) {
        String table = TABLES.get(type);
        if (table == null) {
            throw new IllegalArgumentException("未知位置层类型: " + type);
        }
        return table;
    }

    /** 抢锁结果：claimed=true 时 occupiedBy 即本人（续锁/抢到过期锁）；false 时为当前占用者。 */
    public record ClaimResult(boolean claimed, String occupiedBy, LocalDateTime occupiedAt) {
    }
}
