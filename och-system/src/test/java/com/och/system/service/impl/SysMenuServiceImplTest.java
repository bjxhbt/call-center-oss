package com.och.system.service.impl;

import cn.hutool.core.lang.tree.Tree;
import com.och.common.exception.RequestException;
import com.och.system.domain.entity.SysMenu;
import com.och.system.domain.query.menu.SysMenuAddQuery;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SysMenuServiceImplTest {

    private final SysMenuServiceImpl service = new SysMenuServiceImpl();

    @Test
    void shouldPromoteSelfReferencedMenuToRootWhenBuildingTree() {
        SysMenu menu = menu(10L, 10L, "异常菜单");

        List<Tree<Long>> tree = service.buildMenuTree(List.of(menu));

        assertEquals(1, tree.size());
        assertEquals(10L, tree.get(0).getId());
        assertEquals(0L, tree.get(0).getParentId());
    }

    @Test
    void shouldRejectMenuWhoseParentIsItself() {
        SysMenuAddQuery query = new SysMenuAddQuery();
        query.setMenuId(10L);
        query.setParentId(10L);

        RequestException exception = assertThrows(RequestException.class, () -> service.edit(query));

        assertEquals(1101, exception.getCode());
        assertEquals("父级菜单不能是当前菜单或其子菜单", exception.getMessage());
    }

    @Test
    void shouldExcludeDisabledMenuAndItsChildrenFromRouterTree() {
        SysMenu disabledParent = menu(20L, 0L, "disabled parent");
        disabledParent.setStatus(1);
        SysMenu enabledChild = menu(21L, 20L, "enabled child");
        enabledChild.setStatus(0);
        SysMenu enabledRoot = menu(30L, 0L, "enabled root");
        enabledRoot.setStatus(0);

        List<Tree<Long>> tree = service.buildRouterTree(List.of(disabledParent, enabledChild, enabledRoot));

        assertEquals(1, tree.size());
        assertEquals(30L, tree.get(0).getId());
    }

    @Test
    void shouldKeepDisabledMenuInManagementTree() {
        SysMenu disabledMenu = menu(40L, 0L, "disabled menu");
        disabledMenu.setStatus(1);

        List<Tree<Long>> tree = service.buildMenuTree(List.of(disabledMenu));

        assertEquals(1, tree.size());
        assertEquals(1, tree.get(0).get("status"));
    }

    private SysMenu menu(Long menuId, Long parentId, String menuName) {
        SysMenu menu = new SysMenu();
        menu.setMenuId(menuId);
        menu.setParentId(parentId);
        menu.setMenuName(menuName);
        menu.setOrderNum(1);
        return menu;
    }
}
