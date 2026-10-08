package com.cim.system.menu;

import java.util.List;
import java.util.Objects;

/**
 * 菜单树节点（对外返回结构，避免前端拿扁平列表自行拼树）。
 *
 * @param menu     菜单本体
 * @param children 子节点
 */
public record MenuNode(SysMenu menu, List<MenuNode> children) {

    public MenuNode {
        Objects.requireNonNull(menu, "menu");
        children = children == null ? List.of() : children;
    }
}
