package com.och.esl.service;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.och.common.constant.EslConstant;
import com.och.common.domain.CallInfo;
import com.och.esl.client.FsClient;
import com.och.system.domain.entity.CallHoliday;
import com.och.system.domain.entity.CallSchedule;
import com.och.system.domain.query.holiday.CallHolidayQuery;
import com.och.system.domain.vo.file.VoiceFileVo;
import com.och.system.service.ICallHolidayService;
import com.och.system.service.ICallScheduleService;
import com.och.system.service.IVoiceFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTransferScheduleService {

    private static final String DEFAULT_OFF_HOURS_TEXT = "您好，当前为非工作时间，请您在工作时间致电，谢谢。";

    private final ICallScheduleService callScheduleService;
    private final ICallHolidayService callHolidayService;
    private final IVoiceFileService voiceFileService;
    private final CallPlaybackCompletionService playbackCompletionService;

    /**
     * 破循环依赖：FsClient → ESL事件服务 → 事件工厂 → 事件处理器 → 本服务 → FsClient。
     * 本服务必须用 @Lazy 字段注入，构造器注入会让 Spring 启动报循环依赖(默认禁止)。
     */
    @Lazy
    @Autowired
    private FsClient fsClient;

    @Value("${och.ivr.off-hours-prompt:/opt/och/ivr-audio/xiaban.wav}")
    private String defaultOffHoursPrompt;

    public boolean allowTransfer(String address, CallInfo callInfo, String uniqueId) {
        CallSchedule schedule = findCurrentSchedule();
        if (isWorkingSchedule(schedule)) {
            log.info("人工转接命中工作时间 callId:{} schedule:{}", callInfo.getCallId(), schedule.getName());
            return true;
        }
        String period = isRestSchedule(schedule) ? "休息时间" : "非工作时间";
        log.info("人工转接被日程拦截 callId:{} period:{} schedule:{}",
                callInfo.getCallId(), period, schedule == null ? null : schedule.getName());
        playUnavailablePrompt(address, callInfo, uniqueId, schedule);
        return false;
    }

    public boolean isWorkingSchedule(CallSchedule schedule) {
        return schedule != null && Objects.equals(schedule.getTimeType(), 1);
    }

    public boolean isRestSchedule(CallSchedule schedule) {
        return schedule != null && Objects.equals(schedule.getTimeType(), 3);
    }

    public CallSchedule findCurrentScheduleForNotification() {
        return findCurrentSchedule();
    }

    private CallSchedule findCurrentSchedule() {
        List<CallSchedule> schedules = callScheduleService.list();
        if (schedules == null || schedules.isEmpty()) {
            return null;
        }
        Integer holidayType = getTodayHolidayType();
        return schedules.stream()
                .filter(Objects::nonNull)
                .filter(schedule -> schedule.getTimeType() != null)
                .filter(schedule -> matchesSchedule(schedule, holidayType))
                .sorted(Comparator.comparing(
                        CallSchedule::getLevel, Comparator.nullsLast(Comparator.reverseOrder())))
                .findFirst()
                .orElse(null);
    }

    private boolean matchesSchedule(CallSchedule schedule, Integer holidayType) {
        if (Objects.equals(holidayType, 1)) {
            return Objects.equals(schedule.getTimeType(), 3) && matchesTime(schedule);
        }
        if (Objects.equals(holidayType, 2)) {
            return Objects.equals(schedule.getTimeType(), 1) && matchesTime(schedule);
        }
        return matchesDateTimeAndCycle(schedule);
    }

    private Integer getTodayHolidayType() {
        try {
            CallHolidayQuery query = new CallHolidayQuery();
            query.setHolidayDate(DateUtil.today());
            List<CallHoliday> holidays = callHolidayService.getList(query);
            return holidays == null || holidays.isEmpty() ? null : holidays.get(0).getType();
        } catch (Exception exception) {
            log.warn("人工转接查询节假日失败: {}", exception.getMessage());
            return null;
        }
    }

    private boolean matchesDateTimeAndCycle(CallSchedule schedule) {
        try {
            boolean dayMatched;
            if (Objects.equals(schedule.getType(), 0)) {
                DateTime today = DateUtil.parseDate(DateUtil.today());
                DateTime startDay = DateUtil.parseDate(schedule.getStartDay());
                DateTime endDay = DateUtil.parseDate(schedule.getEndDay());
                dayMatched = today.isAfterOrEquals(startDay) && today.isBeforeOrEquals(endDay);
            } else if (Objects.equals(schedule.getType(), 1)) {
                int dayOfMonth = DateUtil.thisDayOfMonth();
                dayMatched = Integer.parseInt(schedule.getStartDay()) <= dayOfMonth
                        && dayOfMonth <= Integer.parseInt(schedule.getEndDay());
            } else {
                return false;
            }
            int dayOfWeek = DateUtil.date().dayOfWeek() == 1 ? 7 : DateUtil.date().dayOfWeek() - 1;
            String workCycle = schedule.getWorkCycle();
            boolean cycleMatched = workCycle != null
                    && List.of(workCycle.split(",")).contains(String.valueOf(dayOfWeek));
            return dayMatched && cycleMatched && matchesTime(schedule);
        } catch (Exception exception) {
            log.warn("人工转接日程配置无效 scheduleId:{} schedule:{} error:{}",
                    schedule.getId(), schedule.getName(), exception.getMessage());
            return false;
        }
    }

    private boolean matchesTime(CallSchedule schedule) {
        if (schedule.getStartTime() == null || schedule.getEndTime() == null) {
            return false;
        }
        try {
            DateTime startTime = DateUtil.parse(schedule.getStartTime(), "HH:mm");
            DateTime endTime = DateUtil.parse(schedule.getEndTime(), "HH:mm");
            DateTime currentTime = DateUtil.parse(DateUtil.format(new Date(), "HH:mm"), "HH:mm");
            return startTime.isBeforeOrEquals(currentTime) && endTime.isAfterOrEquals(currentTime);
        } catch (Exception exception) {
            return false;
        }
    }

    private void playUnavailablePrompt(String address, CallInfo callInfo, String uniqueId, CallSchedule schedule) {
        String playPath = resolveScheduleVoice(schedule);
        String fallbackPath = null;
        if (playPath == null && isPlayable(defaultOffHoursPrompt)) {
            playPath = defaultOffHoursPrompt;
        } else if (playPath != null && isPlayable(defaultOffHoursPrompt)
                && !playPath.equals(defaultOffHoursPrompt)) {
            fallbackPath = defaultOffHoursPrompt;
        }
        if (playPath == null) {
            String voiceText = schedule == null ? null : schedule.getVoiceText();
            if (voiceText == null || voiceText.trim().isEmpty()) {
                voiceText = DEFAULT_OFF_HOURS_TEXT;
            }
            fsClient.sendArgs(address, uniqueId, EslConstant.SET, "tts_engine=unimrcp");
            fsClient.sendArgs(address, uniqueId, EslConstant.SET, "tts_voice=och");
            playPath = "say:" + voiceText + "'";
        }
        fsClient.sendSyncMsg(address, EslConstant.SETVAR, uniqueId + " playback_terminators none");
        playbackCompletionService.armHangupAfterPlayback(callInfo.getCallId(), uniqueId, fallbackPath);
        log.info("播放人工转接不可用提示 callId:{} uniqueId:{} path:{}",
                callInfo.getCallId(), uniqueId, playPath);
        fsClient.playFile(address, uniqueId, playPath);
    }

    private String resolveScheduleVoice(CallSchedule schedule) {
        if (schedule == null || schedule.getVoiceFileId() == null) {
            return null;
        }
        VoiceFileVo voiceFile = voiceFileService.getDetail(schedule.getVoiceFileId());
        if (voiceFile == null) {
            return null;
        }
        String playPath = voiceFile.getFilePath() == null || voiceFile.getFilePath().isEmpty()
                ? voiceFile.getFileName() : voiceFile.getFilePath();
        return isPlayable(playPath) ? playPath : null;
    }

    private boolean isPlayable(String playPath) {
        if (playPath == null || playPath.trim().isEmpty()) {
            return false;
        }
        if (playPath.contains("://") || playPath.startsWith("say:")) {
            return true;
        }
        try {
            return Files.isReadable(Path.of(playPath));
        } catch (InvalidPathException exception) {
            return false;
        }
    }
}
