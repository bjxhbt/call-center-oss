package com.och.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.tree.Tree;
import cn.hutool.core.lang.tree.TreeNodeConfig;
import cn.hutool.core.lang.tree.TreeUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.och.common.base.BaseServiceImpl;
import com.och.common.enums.DeleteStatusEnum;
import com.och.common.exception.CommonException;
import com.och.system.domain.entity.KbCategory;
import com.och.system.domain.query.kb.KbCategoryAddQuery;
import com.och.system.domain.query.kb.KbCategoryQuery;
import com.och.system.mapper.KbCategoryMapper;
import com.och.system.service.IKbCategoryService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class KbCategoryServiceImpl extends BaseServiceImpl<KbCategoryMapper, KbCategory> implements IKbCategoryService {

    @Override
    public void add(KbCategoryAddQuery query) {
        KbCategory exists = checkName(query);
        if (null != exists) {
            throw new CommonException("分类名称已存在！");
        }
        KbCategory category = new KbCategory();
        BeanUtil.copyProperties(query, category);
        save(category);
    }

    @Override
    public void edit(KbCategoryAddQuery query) {
        KbCategory exists = checkName(query);
        if (null != exists) {
            throw new CommonException("分类名称已存在！");
        }
        KbCategory category = new KbCategory();
        BeanUtil.copyProperties(query, category);
        category.setId(query.getId());
        updateById(category);
    }

    @Override
    public void delete(KbCategoryQuery query) {
        if (query.getId() == null) {
            throw new CommonException("分类ID不能为空");
        }
        KbCategory category = getById(query.getId());
        if (category != null && category.getFlag() != null && category.getFlag() == 1) {
            throw new CommonException("该分类不可删除");
        }
        KbCategory del = new KbCategory();
        del.setDelFlag(DeleteStatusEnum.DELETE_YES.getIndex());
        update(del, new LambdaQueryWrapper<KbCategory>()
                .and(item -> item.eq(KbCategory::getId, query.getId()).or().eq(KbCategory::getParentId, query.getId())));
    }

    @Override
    public KbCategory getDetail(Long id) {
        return getById(id);
    }

    @Override
    public List<Tree<Long>> treeList(KbCategoryQuery query) {
        List<KbCategory> list = new ArrayList<>();
        KbCategory root = new KbCategory();
        root.setId(0L);
        root.setName("全部分类");
        root.setFlag(1);
        root.setParentId(0L);
        list.add(root);

        LambdaQueryWrapper<KbCategory> wrapper = new LambdaQueryWrapper<KbCategory>()
                .eq(KbCategory::getDelFlag, DeleteStatusEnum.DELETE_NO.getIndex());
        if (query != null && query.getName() != null && !query.getName().isEmpty()) {
            wrapper.like(KbCategory::getName, query.getName());
        }
        list.addAll(list(wrapper));

        TreeNodeConfig config = new TreeNodeConfig();
        config.setIdKey("id");
        config.setWeightKey("sort");
        config.setParentIdKey("parentId");
        return TreeUtil.build(list, 0L, config, ((category, treeNode) -> {
            treeNode.setId(category.getId());
            treeNode.setParentId(category.getParentId());
            treeNode.putExtra("title", category.getName());
            treeNode.putExtra("flag", category.getFlag());
            treeNode.putExtra("key", category.getId());
        }));
    }

    @Override
    public List<KbCategory> list(KbCategoryQuery query) {
        LambdaQueryWrapper<KbCategory> wrapper = new LambdaQueryWrapper<KbCategory>()
                .eq(KbCategory::getDelFlag, DeleteStatusEnum.DELETE_NO.getIndex());
        if (query != null) {
            if (query.getName() != null && !query.getName().isEmpty()) {
                wrapper.like(KbCategory::getName, query.getName());
            }
            if (query.getParentId() != null) {
                wrapper.eq(KbCategory::getParentId, query.getParentId());
            }
        }
        return list(wrapper);
    }

    private KbCategory checkName(KbCategoryAddQuery query) {
        return this.getOne(new LambdaQueryWrapper<KbCategory>()
                .eq(KbCategory::getName, query.getName())
                .eq(KbCategory::getParentId, query.getParentId())
                .eq(KbCategory::getDelFlag, DeleteStatusEnum.DELETE_NO.getIndex())
                .ne(query.getId() != null, KbCategory::getId, query.getId()));
    }
}
