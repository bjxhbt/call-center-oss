package com.och.api.controller.ai;

import com.och.ai.asr.config.AsrProperties;
import com.och.ai.asr.ingest.IAsrAudioIngestService;
import com.och.ai.asr.model.AsrProviderType;
import com.och.ai.asr.provider.AsrProviderRegistry;
import com.och.ai.asr.service.IAsrTranscriptService;
import com.och.ai.asr.session.AsrSessionManager;
import com.och.ai.asr.simulator.MockRecordingAudioFeeder;
import com.och.api.websocket.AsrAudioWebSocketHandler;
import com.och.common.base.BaseController;
import com.och.common.base.ResResult;
import com.och.esl.client.FsAudioStreamModule;
import com.och.esl.client.FsClient;
import com.och.esl.client.FsConnectionStatus;
import com.och.esl.event.CallBridgeEvent;
import com.och.esl.event.CallHangupEvent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 实时转写联调/状态接口（方案 9.5 / 并行启动）。
 *
 * <p>提供：ASR 服务状态查询、以及在无 FreeSWITCH 环境下用本地录音或裸 PCM <b>同步</b>跑通
 * “桥接→创建会话→音频→挂断→落库”全链路的模拟入口，便于联调与演示。</p>
 *
 * <p>真实链路由 ESL 事件驱动（CallBridgeEvent/CallHangupEvent），本接口仅用于联调/测试。</p>
 */
@Slf4j
@Tag(name = "实时转写联调")
@RestController
@RequestMapping("asr/v1/admin")
public class AsrAdminController extends BaseController {

    @Autowired
    private AsrProperties properties;
    @Autowired
    private AsrProviderRegistry providerRegistry;
    @Autowired
    private AsrSessionManager sessionManager;
    @Autowired
    private IAsrAudioIngestService audioIngest;
    @Autowired
    private MockRecordingAudioFeeder feeder;
    @Autowired
    private IAsrTranscriptService transcriptService;
    @Autowired
    @Lazy
    private FsClient fsClient;
    @Autowired(required = false)
    private AsrAudioWebSocketHandler audioWebSocketHandler;

    @PreAuthorize("@authz.hasPerm('ai:asr:status')")
    @Operation(summary = "ASR 服务状态(启用/主用/就绪Provider/活动会话数)", method = "POST")
    @PostMapping("/status")
    public ResResult<Map<String, Object>> status() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", properties.isEnabled());
        data.put("configuredProvider", properties.getProvider());
        Map<String, Boolean> ready = new LinkedHashMap<>();
        for (AsrProviderType t : AsrProviderType.values()) {
            ready.put(t.name(), providerRegistry.isReady(t));
        }
        data.put("readyProviders", ready);
        data.put("resolvedProvider", providerRegistry.resolveType(properties.getProvider()));
        data.put("activeSessions", sessionManager.activeCount());
        data.put("sampleRate", properties.getSampleRate());
        data.put("inputCodec", properties.getInputCodec());
        data.put("freeSwitch", freeSwitchStatus());
        data.put("audioIngestWebSocket", audioWebSocketHandler == null
                ? Map.of("registered", false)
                : audioWebSocketHandler.snapshot());
        return success(data);
    }

    private Map<String, Object> freeSwitchStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        String commandAddress = null;
        FsConnectionStatus connectionStatus = null;
        try {
            connectionStatus = fsClient.connectionStatus();
            if (connectionStatus != null) {
                commandAddress = connectionStatus.commandAddress();
            }
        } catch (Exception e) {
            log.warn("Query FreeSWITCH connection status failed: {}", e.getMessage());
        }
        // Keep the status endpoint compatible with mocked/custom FsClient
        // implementations that predate connectionStatus().
        if (!StringUtils.hasText(commandAddress)) {
            try {
                commandAddress = fsClient.anyAddress();
            } catch (Exception e) {
                log.warn("Query FreeSWITCH command connection status failed: {}", e.getMessage());
            }
        }
        AsrProperties.AudioStream stream = properties.getAudioStream();
        boolean streamConfigured = properties.isEnabled()
                && stream != null
                && stream.isEnabled()
                && StringUtils.hasText(stream.getModule())
                && !"none".equalsIgnoreCase(stream.getModule().trim());
        boolean commandChannelReady = StringUtils.hasText(commandAddress);
        boolean eslConnected = commandChannelReady
                || (connectionStatus != null && connectionStatus.eventChannelActive());
        String address = commandChannelReady ? commandAddress
                : connectionStatus == null ? null : connectionStatus.bestKnownAddress();
        FsAudioStreamModule selectedModule = stream == null
                ? null : FsAudioStreamModule.resolve(stream.getModule());
        boolean endpointConfigured = stream != null && StringUtils.hasText(stream.getEndpoint());
        boolean endpointCompatible = selectedModule != null
                && endpointConfigured
                && selectedModule.supportsEndpoint(stream.getEndpoint());
        Boolean moduleAvailable = null;
        if (commandChannelReady && streamConfigured && selectedModule != null) {
            try {
                String moduleExists = fsClient.sendApiText(commandAddress, "module_exists",
                        selectedModule.freeSwitchModule());
                moduleAvailable = StringUtils.hasText(moduleExists)
                        && "true".equalsIgnoreCase(moduleExists.trim());
            } catch (Exception e) {
                log.warn("Query {} status failed: {}", selectedModule.freeSwitchModule(), e.getMessage());
                moduleAvailable = false;
            }
        }
        boolean streamOperational = streamConfigured && commandChannelReady
                && selectedModule != null
                && endpointCompatible
                && Boolean.TRUE.equals(moduleAvailable);
        status.put("eslConnected", eslConnected);
        status.put("eslAddress", address);
        status.put("eslCommandReady", commandChannelReady);
        status.put("eslCommandAddress", commandAddress);
        status.put("eslEventAddress", connectionStatus == null ? null : connectionStatus.eventAddress());
        status.put("eslEventLastSeenAt", connectionStatus == null ? null : connectionStatus.lastEventAtEpochMillis());
        status.put("eslEventAgeMillis", connectionStatus == null ? null : connectionStatus.eventAgeMillis());
        status.put("audioStreamEnabled", streamConfigured);
        status.put("audioStreamOperational", streamOperational);
        status.put("audioStreamModule", stream == null ? null : stream.getModule());
        status.put("audioStreamModuleSupported", selectedModule != null);
        status.put("audioStreamFsModule", selectedModule == null ? null : selectedModule.freeSwitchModule());
        status.put("audioStreamModuleAvailable", moduleAvailable);
        // Backward-compatible field retained for existing status UI/automation.
        status.put("audioForkAvailable", selectedModule == FsAudioStreamModule.AUDIO_FORK
                ? moduleAvailable : null);
        status.put("audioStreamSampleRate", stream == null ? null : stream.getSampleRate());
        status.put("bothLegs", stream != null && stream.isBothLegs());
        status.put("streamEndpointConfigured", endpointConfigured);
        status.put("streamEndpointTransport", stream == null
                ? null : FsAudioStreamModule.endpointTransport(stream.getEndpoint()));
        status.put("streamEndpointCompatible", endpointCompatible);
        status.put("streamTokenConfigured", stream != null && StringUtils.hasText(stream.getStreamToken()));
        return status;
    }

    @PreAuthorize("@authz.hasPerm('ai:asr:simulate')")
    @Operation(summary = "模拟一次转写(本地录音/裸PCM→全链路→落库)，无FS环境联调用", method = "POST")
    @PostMapping("/simulate")
    public ResResult<Map<String, Object>> simulate(@RequestBody SimulateRequest req) throws Exception {
        if (req.getCallId() == null) {
            return error("callId 不能为空");
        }
        String uniqueId = req.getUniqueId() == null ? ("sim-" + req.getCallId()) : req.getUniqueId();
        long now = System.currentTimeMillis();
        // 1) 桥接：创建会话（直接同步调用，非事件，便于返回结果）
        CallBridgeEvent bridge = CallBridgeEvent.builder()
                .callId(req.getCallId())
                .uniqueId(uniqueId)
                .otherUniqueId(req.getOtherUniqueId())
                .caller(req.getCaller())
                .callee(req.getCallee())
                .agentId(req.getAgentId())
                .agentNumber(req.getAgentNumber())
                .agentName(req.getAgentName())
                .direction(req.getDirection())
                .bridgeTime(now)
                .record(req.getWavPath())
                .build();
        sessionManager.onBridge(bridge);

        // 2) 喂音频
        int frames = 0;
        int sampleRate = req.getSampleRate() == null ? properties.getSampleRate() : req.getSampleRate();
        int channels = req.getChannels() == null ? 1 : req.getChannels();
        int frameMs = req.getFrameDurationMs() == null ? properties.getFrameDurationMs() : req.getFrameDurationMs();
        try {
            if (req.getWavPath() != null && !req.getWavPath().isBlank()) {
                frames = feeder.feed(req.getWavPath(), req.getCallId(), uniqueId, frameMs, false, 0,
                        req.getSpeaker(), audioIngest::onFrame);
            } else if (req.getBase64Pcm() != null && !req.getBase64Pcm().isBlank()) {
                byte[] pcm = Base64.getDecoder().decode(req.getBase64Pcm().getBytes(StandardCharsets.UTF_8));
                frames = feeder.feedRawPcm(pcm, sampleRate, channels, req.getCallId(), uniqueId, frameMs, false, 0,
                        req.getSpeaker(), audioIngest::onFrame);
            }
        } catch (Exception e) {
            log.warn("模拟喂音频异常 callId:{} err:{}", req.getCallId(), e.getMessage());
        }

        // 3) 挂断：结束会话并落库摘要（同步等待最终结果）
        CallHangupEvent hangup = CallHangupEvent.builder()
                .callId(req.getCallId())
                .uniqueId(uniqueId)
                .caller(req.getCaller())
                .callee(req.getCallee())
                .agentId(req.getAgentId())
                .agentNumber(req.getAgentNumber())
                .direction(req.getDirection())
                .endTime(System.currentTimeMillis())
                .record(req.getWavPath())
                .build();
        sessionManager.onHangup(hangup);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("framesSent", frames);
        data.put("resolvedProvider", providerRegistry.resolveType(properties.getProvider()));
        var summary = transcriptService.getByCallId(String.valueOf(req.getCallId()));
        if (summary != null) {
            data.put("summary", transcriptService.getDetail(summary.getId()));
        }
        return success(data);
    }

    /** 模拟请求。 */
    @Data
    public static class SimulateRequest {
        private Long callId;
        private String uniqueId;
        private String otherUniqueId;
        private String caller;
        private String callee;
        private Long agentId;
        private String agentNumber;
        private String agentName;
        private Integer direction;
        /** 本地 WAV 路径（classpath: 或绝对路径）。 */
        private String wavPath;
        /** 裸 16bit PCM（小端）的 Base64。 */
        private String base64Pcm;
        private Integer sampleRate;
        private Integer channels;
        private Integer frameDurationMs;
        /** 说话人（CUSTOMER/AGENT/UNKNOWN）。 */
        private com.och.ai.asr.model.Speaker speaker;
    }
}
