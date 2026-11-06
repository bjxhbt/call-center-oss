package com.och.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.pagehelper.PageInfo;
import com.och.common.base.BaseServiceImpl;
import com.och.common.enums.DeleteStatusEnum;
import com.och.common.exception.CommonException;
import com.och.system.domain.entity.KbEntry;
import com.och.system.domain.query.kb.KbEntryAddQuery;
import com.och.system.domain.query.kb.KbEntryQuery;
import com.och.system.mapper.KbEntryMapper;
import com.och.system.service.IKbEntryService;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;

@Service
public class KbEntryServiceImpl extends BaseServiceImpl<KbEntryMapper, KbEntry> implements IKbEntryService {

    @Override
    public void add(KbEntryAddQuery query) {
        KbEntry entry = new KbEntry();
        BeanUtil.copyProperties(query, entry);
        save(entry);
    }

    @Override
    public void edit(KbEntryAddQuery query) {
        if (query.getId() == null) {
            throw new CommonException("词条ID不能为空");
        }
        KbEntry entry = new KbEntry();
        BeanUtil.copyProperties(query, entry);
        updateById(entry);
    }

    @Override
    public void delete(KbEntryQuery query) {
        if (query.getId() == null) {
            throw new CommonException("词条ID不能为空");
        }
        KbEntry del = new KbEntry();
        del.setDelFlag(DeleteStatusEnum.DELETE_YES.getIndex());
        update(del, new LambdaQueryWrapper<KbEntry>().eq(KbEntry::getId, query.getId()));
    }

    @Override
    public KbEntry getDetail(Long id) {
        return getById(id);
    }

    @Override
    public PageInfo<KbEntry> pageList(KbEntryQuery query) {
        startPage(query.getPageIndex(), query.getPageSize(), query.getSortField(), query.getSort());
        List<KbEntry> list = baseMapper.getList(query);
        return new PageInfo<>(list);
    }

    @Override
    public List<KbEntry> list(KbEntryQuery query) {
        return baseMapper.getList(query);
    }
}
