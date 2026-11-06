package com.och.esl.service;

import com.och.common.constant.EslConstant;
import com.och.common.domain.CallInfo;
import com.och.common.enums.HangupCauseEnum;
import com.och.esl.client.FsClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AgentUnavailablePromptServiceTest {

    private IFsCallCacheService cacheService;
    private CallPlaybackCompletionService playbackCompletionService;
    private FsClient fsClient;
    private AgentUnavailablePromptService service;

    @BeforeEach
    void setUp() {
        cacheService = mock(IFsCallCacheService.class);
        playbackCompletionService = mock(CallPlaybackCompletionService.class);
        fsClient = mock(FsClient.class);
        service = new AgentUnavailablePromptService(cacheService, playbackCompletionService);
        ReflectionTestUtils.setField(service, "fsClient", fsClient);
        ReflectionTestUtils.setField(service, "agentBusyPrompt", "/opt/och/ivr-audio/agent_busy.wav");
    }

    @AfterEach
    void tearDown() {
        service.destroy();
    }

    @Test
    void playsConfiguredPromptAndArmsHangupWithSpeechFallback() {
        CallInfo callInfo = CallInfo.builder().callId(1001L).build();

        service.playAndHangup("127.0.0.1:8021", callInfo, "caller-uuid", HangupCauseEnum.FULLBUSY);

        assertThat(callInfo.getHangupDir()).isEqualTo(3);
        assertThat(callInfo.getHangupCause()).isEqualTo(HangupCauseEnum.FULLBUSY.getCode());
        verify(cacheService).saveCallInfo(callInfo);
        verify(playbackCompletionService).armHangupAfterPlayback(
                1001L, "caller-uuid", "say:当前坐席繁忙，请稍后重试。");
        verify(fsClient).sendSyncMsg(
                "127.0.0.1:8021", EslConstant.SETVAR, "caller-uuid playback_terminators none");
        verify(fsClient).playFile(
                "127.0.0.1:8021", "caller-uuid", "/opt/och/ivr-audio/agent_busy.wav");
    }
}
