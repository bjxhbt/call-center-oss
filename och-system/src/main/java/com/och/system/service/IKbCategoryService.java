package com.och.system.service;

import cn.hutool.core.lang.tree.Tree;
import com.och.common.base.IBaseService;
import com.och.system.domain.entity.KbCategory;
import com.och.system.domain.query.kb.KbCategoryAddQuery;
import com.och.system.domain.query.kb.KbCategoryQuery;

import java.util.List;

public interface IKbCategoryService extends IBaseService<KbCategory> {

    void add(KbCategoryAddQuery query);

    void edit(KbCategoryAddQuery query);

    void delete(KbCategoryQuery query);

    KbCategory getDetail(Long id);

    List<Tree<Long>> treeList(KbCategoryQuery query);

    List<KbCategory> list(KbCategoryQuery query);
}
