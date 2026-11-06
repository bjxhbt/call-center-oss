package com.och.esl.service;

import com.och.common.constant.EslConstant;
import com.och.common.domain.CallInfo;
import com.och.common.enums.HangupCauseEnum;
import com.och.common.thread.ThreadFactoryImpl;
import com.och.esl.client.FsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentUnavailablePromptService implements DisposableBean {

    private static final String DEFAULT_BUSY_PROMPT = "say:当前坐席繁忙，请稍后重试。";
    private static final long PLAYBACK_TIMEOUT_SECONDS = 10;

    private final IFsCallCacheService fsCallCacheService;
    private final CallPlaybackCompletionService playbackCompletionService;
    private final ScheduledExecutorService timeoutExecutor = new ScheduledThreadPoolExecutor(
            1, new ThreadFactoryImpl("agent-unavailable-prompt-%d"));

    @Lazy
    @Autowired
    private FsClient fsClient;

    @Value("${och.ivr.agent-busy-prompt:" + DEFAULT_BUSY_PROMPT + "}")
    private String agentBusyPrompt;

    public void playAndHangup(String address, CallInfo callInfo, String uniqueId, HangupCauseEnum cause) {
        if (callInfo == null || callInfo.getCallId() == null || StringUtils.isBlank(uniqueId)) {
            log.warn("Cannot play agent unavailable prompt, callId:{}, uniqueId:{}",
                    callInfo == null ? null : callInfo.getCallId(), uniqueId);
            return;
        }
        HangupCauseEnum effectiveCause = cause == null ? HangupCauseEnum.FULLBUSY : cause;
        String playPath = StringUtils.isBlank(agentBusyPrompt) ? DEFAULT_BUSY_PROMPT : agentBusyPrompt;
        String fallbackPath = StringUtils.startsWithIgnoreCase(playPath, "say:")
                ? null : DEFAULT_BUSY_PROMPT;

        callInfo.setHangupDir(3);
        callInfo.setHangupCause(effectiveCause.getCode());
        if (callInfo.getDetailList() != null) {
            callInfo.setSkillHangUpReason(effectiveCause.getDesc());
        }
        fsCallCacheService.saveCallInfo(callInfo);

        playbackCompletionService.armHangupAfterPlayback(callInfo.getCallId(), uniqueId, fallbackPath);
        fsClient.sendSyncMsg(address, EslConstant.SETVAR, uniqueId + " playback_terminators none");
        fsClient.playFile(address, uniqueId, playPath);
        timeoutExecutor.schedule(() -> forceHangupAfterTimeout(address, callInfo.getCallId(), uniqueId),
                PLAYBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        log.info("Agent unavailable prompt started, callId:{}, uniqueId:{}, path:{}",
                callInfo.getCallId(), uniqueId, playPath);
    }

    private void forceHangupAfterTimeout(String address, Long callId, String uniqueId) {
        if (playbackCompletionService.consumeHangupAfterPlayback(callId, uniqueId)) {
            log.warn("Agent unavailable prompt timed out; forcing hangup, callId:{}, uniqueId:{}",
                    callId, uniqueId);
            fsClient.hangupCall(address, callId, uniqueId);
        }
    }

    @Override
    public void destroy() {
        timeoutExecutor.shutdownNow();
    }
}
