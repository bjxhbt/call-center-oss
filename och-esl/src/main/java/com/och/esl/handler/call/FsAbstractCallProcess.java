package com.och.esl.handler.call;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.och.common.constant.SysSettingConfig;
import com.och.common.domain.CallInfo;
import com.och.common.domain.ChannelInfo;
import com.och.esl.client.FsClient;
import com.och.esl.factory.FsEslRouteFactory;
import com.och.esl.service.IFsCallCacheService;
import com.och.system.domain.entity.CallHoliday;
import com.och.system.domain.entity.CallSchedule;
import com.och.system.domain.query.holiday.CallHolidayQuery;
import com.och.system.service.ICallHolidayService;
import com.och.system.service.ICallInPhoneService;
import com.och.system.service.ICallTraceStore;
import java.util.List;
import com.och.system.service.ICallScheduleService;
import com.och.system.service.IFsSipGatewayService;
import lombok.extern.slf4j.Slf4j;
import org.freeswitch.esl.client.transport.event.EslEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Date;

@Slf4j
@Component
public abstract class FsAbstractCallProcess {

    @Lazy
    @Autowired
    protected FsClient fsClient;

    @Autowired
    protected SysSettingConfig sysSettingConfig;

    @Autowired
    protected IFsCallCacheService lfsCallCacheService;

    @Autowired
    protected FsEslRouteFactory routeFactory;

    @Autowired
    protected ICallInPhoneService callInPhoneService;

    @Autowired
    protected IFsSipGatewayService fsSipGatewayService;

    @Autowired
    protected ICallScheduleService callScheduleService;;

    @Autowired
    protected ICallHolidayService callHolidayService;

    @Autowired
    protected ICallTraceStore callTraceStore;

    /**
     * 查询今天是否为特殊日(假期/调休)
     * @return null=普通日, 1=假期(休息), 2=调休(上班)
     */
    protected Integer getTodayHolidayType() {
        try {
            CallHolidayQuery q = new CallHolidayQuery();
            q.setHolidayDate(DateUtil.today());
            List<CallHoliday> list = callHolidayService.getList(q);
            if (list != null && !list.isEmpty()) {
                return list.get(0).getType();
            }
        } catch (Exception e) {
            log.warn("查询假期日历失败: {}", e.getMessage());
        }
        return null;
    }

    public abstract void handler(String address, EslEvent event, CallInfo callInfo, ChannelInfo lfsChannelInfo);

    protected Boolean checkSchedule(CallSchedule scheduleDetail) {
        boolean dayFlag = false;
        boolean timeFlag = false;
        boolean weekFlag = false;
        if(scheduleDetail.getType() == 0){
            DateTime startDay = DateUtil.parseDate(scheduleDetail.getStartDay());
            DateTime endDay = DateUtil.parseDate(scheduleDetail.getEndDay());
            DateTime dateTime = DateUtil.parseDate(DateUtil.today());
            dayFlag = dateTime.isAfterOrEquals(startDay) && dateTime.isBeforeOrEquals(endDay);
        }else if(scheduleDetail.getType() == 1){
            int dayOfMonth = DateUtil.thisDayOfMonth();
            dayFlag =  Integer.parseInt(scheduleDetail.getStartDay()) <= dayOfMonth  &&  dayOfMonth <= Integer.parseInt(scheduleDetail.getEndDay());
        }
        DateTime startTime = DateUtil.parse(scheduleDetail.getStartTime(), "HH:mm");
        DateTime endTime = DateUtil.parse(scheduleDetail.getEndTime(), "HH:mm");
        DateTime dateTime = DateUtil.parse(DateUtil.format(new Date(),"HH:mm"),"HH:mm");
        if(startTime.isBeforeOrEquals(dateTime) && endTime.isAfterOrEquals(dateTime)){
            timeFlag = true;
        }
        String workCycle = scheduleDetail.getWorkCycle();
        int dayOfWeek = DateUtil.date().dayOfWeek() == 1 ? 7 :  DateUtil.date().dayOfWeek() -1;
        if(workCycle.contains(String.valueOf(dayOfWeek))){
            weekFlag = true;
        }
        if(dayFlag && timeFlag && weekFlag){
            return true;
        }
        return false;
    }

    /**
     * 仅检查时间是否命中(不含日期/周期判断)
     * 用于假期/调休覆盖
     */
    protected Boolean checkTimeMatched(CallSchedule scheduleDetail) {
        if (scheduleDetail == null || scheduleDetail.getStartTime() == null || scheduleDetail.getEndTime() == null) {
            return false;
        }
        try {
            DateTime startTime = DateUtil.parse(scheduleDetail.getStartTime(), "HH:mm");
            DateTime endTime = DateUtil.parse(scheduleDetail.getEndTime(), "HH:mm");
            DateTime dateTime = DateUtil.parse(DateUtil.format(new Date(), "HH:mm"), "HH:mm");
            return startTime.isBeforeOrEquals(dateTime) && endTime.isAfterOrEquals(dateTime);
        } catch (Exception e) {
            return false;
        }
    }

}
