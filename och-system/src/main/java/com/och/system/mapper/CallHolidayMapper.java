package com.och.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.och.system.domain.entity.CallHoliday;
import com.och.system.domain.query.holiday.CallHolidayQuery;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
@Mapper
public interface CallHolidayMapper extends BaseMapper<CallHoliday> {
    List<CallHoliday> getList(CallHolidayQuery query);
}
