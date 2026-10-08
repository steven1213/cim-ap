package com.cim.system.menu;

import com.cim.system.support.EnableStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜单树构建：扁平列表 → 父子森林。
 *
 * <p>单点收敛构建规则，供两个视角复用：</p>
 * <ul>
 *   <li><b>用户视角</b>（{@code /sys/me/menus}）：先按角色过滤可见菜单，再入树；</li>
 *   <li><b>管理视角</b>（{@code /sys/menus/tree}）：全量菜单入树，供授权界面勾选。</li>
 * </ul>
 *
 * <p><b>父节点不可见时上浮为根</b>：若某节点被授权但其父未被授权（历史数据或授权不完整），
 * 直接丢弃会让整棵子树消失——这是「授权了但看不到」类工单的常见根因，故显式上浮而非静默丢弃。</p>
 */
public final class MenuTrees {

    private MenuTrees() {
    }

    /**
     * 构建菜单树。
     *
     * @param flat           扁平菜单列表（建议已按 {@code sort_no} 排序）
     * @param includeButtons 是否保留 {@link MenuType#BUTTON} 节点
     * @return 顶级节点列表（森林）
     */
    public static List<MenuNode> build(List<SysMenu> flat, boolean includeButtons) {
        if (flat == null || flat.isEmpty()) {
            return List.of();
        }
        Map<String, MenuNode> nodes = new LinkedHashMap<>();
        for (SysMenu menu : flat) {
            if (!includeButtons && menu.getType() == MenuType.BUTTON) {
                continue;
            }
            if (menu.getStatus() == EnableStatus.DISABLED) {
                continue;
            }
            nodes.put(menu.getId(), new MenuNode(menu, new ArrayList<>()));
        }
        List<MenuNode> roots = new ArrayList<>();
        for (MenuNode node : nodes.values()) {
            String parentId = node.menu().getParentId();
            MenuNode parent = (parentId == null || parentId.isBlank()) ? null : nodes.get(parentId);
            if (parent == null) {
                roots.add(node);
            } else {
                parent.children().add(node);
            }
        }
        return roots;
    }
}
