package com.och.system.service.impl;

import cn.hutool.core.lang.tree.Tree;
import cn.hutool.core.lang.tree.TreeNodeConfig;
import cn.hutool.core.lang.tree.TreeUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.och.common.base.BaseServiceImpl;
import com.och.common.enums.DeleteStatusEnum;
import com.och.common.exception.RequestException;
import com.och.system.domain.entity.SysMenu;
import com.och.system.domain.query.menu.SysMenuAddQuery;
import com.och.system.domain.query.menu.SysMenuQuery;
import com.och.system.mapper.SysMenuMapper;
import com.och.system.service.ISysMenuService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 菜单权限表(SysMenu)表服务实现类
 *
 * @author danmo
 * @since 2024-02-22 11:22:25
 */
@Service
public class SysMenuServiceImpl extends BaseServiceImpl<SysMenuMapper, SysMenu> implements ISysMenuService {

    private static final long ROOT_MENU_ID = 0L;
    private static final int ERROR_INVALID_MENU_PARENT = 1101;

    @Override
    public void add(SysMenuAddQuery query) {
        normalizeAndValidateParent(query);
        SysMenu sysMenu = new SysMenu();
        BeanUtils.copyProperties(query, sysMenu);
        save(sysMenu);
    }

    @Override
    public void edit(SysMenuAddQuery query) {
        normalizeAndValidateParent(query);
        SysMenu sysMenu = new SysMenu();
        BeanUtils.copyProperties(query, sysMenu);
        sysMenu.setMenuId(query.getMenuId());
        updateById(sysMenu);
    }

    @Override
    public List<SysMenu> getList(SysMenuQuery query) {
        return this.baseMapper.getList(query);
    }

    @Override
    public void delete(SysMenuQuery query) {
        Optional.ofNullable(query.getMenuIds()).orElseGet(ArrayList::new).add(query.getMenuId());
        if (!CollectionUtils.isEmpty(query.getMenuIds())) {
            update(new LambdaUpdateWrapper<SysMenu>().set(SysMenu::getDelFlag, DeleteStatusEnum.DELETE_YES.getIndex())
                    .and(item -> item.in(SysMenu::getMenuId, query.getMenuIds())).or().in(SysMenu::getParentId,query.getMenuIds()));
        }
    }

    @Override
    public List<Tree<Long>> buildMenuTree(List<SysMenu> list) {
        TreeNodeConfig treeNodeConfig = new TreeNodeConfig();
        Map<Long, SysMenu> menuIndex = new HashMap<>();
        for (SysMenu menu : list) {
            menuIndex.put(menu.getMenuId(), menu);
        }
        //自定义属性名 都要默认值的
        treeNodeConfig.setWeightKey("orderNum");
        treeNodeConfig.setIdKey("menuId");
        treeNodeConfig.setParentIdKey("parentId");
        return TreeUtil.build(list, 0L, treeNodeConfig, ((sysMenu, treeNode) -> {
            treeNode.setId(sysMenu.getMenuId());//id
            treeNode.setParentId(resolveSafeParentId(sysMenu, menuIndex));//父id
            treeNode.putExtra("name", sysMenu.getMenuName());
            treeNode.putExtra("orderNum", sysMenu.getOrderNum());
            treeNode.putExtra("menuType", sysMenu.getMenuType());
            treeNode.putExtra("component", sysMenu.getComponent());
            treeNode.putExtra("iconName", sysMenu.getIcon());
            treeNode.putExtra("isFrame", sysMenu.getIsFrame());
            treeNode.putExtra("perms", sysMenu.getPerms());
            treeNode.putExtra("visible", sysMenu.getVisible());
            treeNode.putExtra("path", sysMenu.getPath());
            treeNode.putExtra("status", sysMenu.getStatus());
            treeNode.putExtra("remark", sysMenu.getRemark());
        }));
    }

    @Override
    public List<Tree<Long>> buildRouterTree(List<SysMenu> list) {
        List<Tree<Long>> trees = buildMenuTree(list);
        trees.removeIf(this::removeDisabledRouterNode);
        return trees;
    }

    private boolean removeDisabledRouterNode(Tree<Long> node) {
        if (!Objects.equals(0, node.get("status"))) {
            return true;
        }
        List<Tree<Long>> children = node.getChildren();
        if (!CollectionUtils.isEmpty(children)) {
            children.removeIf(this::removeDisabledRouterNode);
        }
        return false;
    }

    private void normalizeAndValidateParent(SysMenuAddQuery query) {
        Long parentId = query.getParentId();
        if (parentId == null) {
            query.setParentId(ROOT_MENU_ID);
            return;
        }
        if (parentId == ROOT_MENU_ID) {
            return;
        }

        Set<Long> visited = new HashSet<>();
        Long currentId = parentId;
        while (currentId != null && currentId != ROOT_MENU_ID) {
            if (Objects.equals(currentId, query.getMenuId())) {
                throw invalidParent("父级菜单不能是当前菜单或其子菜单");
            }
            if (!visited.add(currentId)) {
                throw invalidParent("父级菜单层级存在循环，请先修复菜单数据");
            }
            SysMenu currentMenu = getById(currentId);
            if (currentMenu == null) {
                throw invalidParent("父级菜单不存在");
            }
            currentId = currentMenu.getParentId();
        }
    }

    private Long resolveSafeParentId(SysMenu menu, Map<Long, SysMenu> menuIndex) {
        Long parentId = menu.getParentId();
        if (parentId == null || parentId == ROOT_MENU_ID) {
            return ROOT_MENU_ID;
        }

        Set<Long> visited = new HashSet<>();
        Long currentId = parentId;
        while (currentId != null && currentId != ROOT_MENU_ID) {
            if (Objects.equals(currentId, menu.getMenuId()) || !visited.add(currentId)) {
                return ROOT_MENU_ID;
            }
            SysMenu currentMenu = menuIndex.get(currentId);
            if (currentMenu == null) {
                return ROOT_MENU_ID;
            }
            currentId = currentMenu.getParentId();
        }
        return parentId;
    }

    private RequestException invalidParent(String message) {
        return new RequestException(ERROR_INVALID_MENU_PARENT, message);
    }

    @Override
    public List<SysMenu> getMenuListByRoleIds(List<Long> roleIds) {
        return this.baseMapper.getMenuListByRoleIds(roleIds);
    }
}
