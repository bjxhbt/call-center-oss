package com.och.system.service;

import com.och.common.base.IBaseService;
import com.och.system.domain.entity.CallHoliday;
import com.och.system.domain.query.holiday.CallHolidayAddQuery;
import com.och.system.domain.query.holiday.CallHolidayQuery;
import java.util.List;

public interface ICallHolidayService extends IBaseService<CallHoliday> {
    void add(CallHolidayAddQuery query);
    void update(CallHolidayAddQuery query);
    void delete(CallHolidayQuery query);
    List<CallHoliday> getList(CallHolidayQuery query);
    List<CallHoliday> getPageList(CallHolidayQuery query);
}
