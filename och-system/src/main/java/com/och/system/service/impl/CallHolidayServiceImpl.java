package com.och.system.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.och.common.base.BaseServiceImpl;
import com.och.common.enums.DeleteStatusEnum;
import com.och.system.domain.entity.CallHoliday;
import com.och.system.domain.query.holiday.CallHolidayAddQuery;
import com.och.system.domain.query.holiday.CallHolidayQuery;
import com.och.system.mapper.CallHolidayMapper;
import com.och.system.service.ICallHolidayService;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class CallHolidayServiceImpl extends BaseServiceImpl<CallHolidayMapper, CallHoliday> implements ICallHolidayService {
    @Override
    public void add(CallHolidayAddQuery query) {
        if (query.getHolidayDates() != null && !query.getHolidayDates().isEmpty()) {
            java.util.List<CallHoliday> list = new java.util.ArrayList<>();
            for (String date : query.getHolidayDates()) {
                CallHoliday h = new CallHoliday();
                h.setHolidayDate(date);
                h.setType(query.getType());
                h.setName(query.getName());
                list.add(h);
            }
            saveBatch(list);
            return;
        }
        CallHoliday h = new CallHoliday();
        h.setQuery2Entity(query);
        save(h);
    }
    @Override
    public void update(CallHolidayAddQuery query) {
        CallHoliday h = new CallHoliday();
        h.setQuery2Entity(query);
        updateById(h);
    }
    @Override
    public void delete(CallHolidayQuery query) {
        List<Long> ids = new LinkedList<>();
        if (Objects.nonNull(query.getId())) ids.add(query.getId());
        if (CollectionUtil.isNotEmpty(query.getIds())) ids.addAll(query.getIds());
        if (CollectionUtil.isEmpty(ids)) return;
        List<CallHoliday> list = ids.stream().map(id -> {
            CallHoliday h = new CallHoliday();
            h.setId(id);
            h.setDelFlag(DeleteStatusEnum.DELETE_YES.getIndex());
            return h;
        }).collect(Collectors.toList());
        updateBatchById(list);
    }
    @Override
    public List<CallHoliday> getList(CallHolidayQuery query) {
        return this.baseMapper.getList(query);
    }
    @Override
    public List<CallHoliday> getPageList(CallHolidayQuery query) {
        return getList(query);
    }
}