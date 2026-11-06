package com.och.api.controller.ai;

import com.och.ai.tts.model.TtsPlaybackEvent;
import com.och.ai.tts.model.TtsRequest;
import com.och.ai.tts.model.TtsTaskView;
import com.och.ai.tts.config.TtsProperties;
import com.och.ai.tts.entity.TtsFile;
import com.och.ai.tts.file.TtsFileManager;
import com.och.ai.tts.playback.TtsPlaybackPort;
import com.och.ai.tts.provider.TtsProviderRegistry;
import com.och.api.controller.ai.dto.TtsSynthesizeQuery;
import com.och.api.controller.ai.dto.TtsVoiceVo;
import com.och.common.base.BaseController;
import com.och.common.base.ResResult;
import com.och.common.utils.StringUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一 TTS 接口（方案 8.1/9.6 对外入口）。
 *
 * <p>鉴权：合成、任务、播放与 Provider 查询要求 {@code ai:tts:use}；
 * 音频文件读取端点供浏览器 audio 标签与 FreeSWITCH 使用，由安全配置单独放行。</p>
 */
@Slf4j
@Tag(name = "统一语音合成 TTS")
@RestController
@RequestMapping("/tts/v1")
@RequiredArgsConstructor
public class TtsController extends BaseController {

    private final com.och.ai.tts.service.TtsBusinessService ttsBusinessService;
    private final TtsProviderRegistry providerRegistry;
    private final TtsFileManager ttsFileManager;
    private final TtsProperties ttsProperties;
    private final Map<String, TtsPlaybackPort> playbackPorts;

    @Operation(summary = "合成语音（同步返回文件地址，可下发播放）", method = "POST")
    @PreAuthorize("@authz.hasPerm('ai:tts:use')")
    @PostMapping("/synthesize")
    public ResResult<TtsTaskView> synthesize(@RequestBody TtsSynthesizeQuery query) {
        if (query == null || StringUtils.isBlank(query.getText())) {
            return ResResult.error("合成文本不能为空");
        }
        TtsRequest request = TtsRequest.builder()
                .text(query.getText())
                .provider(query.getProvider())
                .voice(query.getVoice())
                .speed(query.getSpeed())
                .pitch(query.getPitch())
                .volume(query.getVolume())
                .sampleRate(query.getSampleRate())
                .format(query.getFormat())
                .callId(query.getCallId())
                .channelUuid(query.getChannelUuid())
                .forceRefresh(Boolean.TRUE.equals(query.getForceRefresh()))
                .skipCache(Boolean.TRUE.equals(query.getSkipCache()))
                .build();
        TtsTaskView view = ttsBusinessService.submit(request);
        // 业务结果而非 HTTP 状态：失败时 data.status=FAILED + errorCode
        return ResResult.success(view);
    }

    @Operation(summary = "查询任务状态", method = "GET")
    @PreAuthorize("@authz.hasPerm('ai:tts:use')")
    @GetMapping("/task/{taskId}")
    public ResResult<TtsTaskView> task(@PathVariable("taskId") String taskId) {
        return ResResult.success(ttsBusinessService.getTask(taskId));
    }

    @Operation(summary = "对已生成文件再次下发播放（复用缓存）", method = "POST")
    @PreAuthorize("@authz.hasPerm('ai:tts:use')")
    @PostMapping("/playback")
    public ResResult<TtsTaskView> playback(@RequestBody Map<String, String> body) {
        String taskId = body.get("taskId");
        String callId = body.get("callId");
        String channelUuid = body.get("channelUuid");
        if (StringUtils.isBlank(taskId)) {
            return ResResult.error("taskId 不能为空");
        }
        return ResResult.success(ttsBusinessService.replay(taskId, callId, channelUuid));
    }

    @Operation(summary = "通信链路回写播放事件（START/COMPLETE/FAILED）", method = "POST")
    @PostMapping("/playback/event")
    public ResResult<Void> playbackEvent(@RequestBody TtsPlaybackEvent event) {
        if (event.getStatus() == null) {
            return ResResult.error("status 不能为空");
        }
        if (event.getTimestamp() == null) {
            event.setTimestamp(System.currentTimeMillis());
        }
        ttsBusinessService.onPlaybackEvent(event);
        return ResResult.success();
    }

    @Operation(summary = "可用 TTS 服务商列表", method = "GET")
    @PreAuthorize("@authz.hasPerm('ai:tts:use')")
    @GetMapping("/providers")
    public ResResult<List<Map<String, Object>>> providers() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (com.och.ai.tts.model.TtsProviderType t : com.och.ai.tts.model.TtsProviderType.values()) {
            list.add(Map.of(
                    "provider", t.name(),
                    "available", providerRegistry.isAvailable(t),
                    "ready", providerRegistry.isReady(t)));
        }
        return ResResult.success(list);
    }

    @Operation(summary = "TTS 播放运行时诊断（唯一 Bean 与实际实现）", method = "GET")
    @PreAuthorize("@authz.hasPerm('ai:tts:use')")
    @GetMapping("/runtime")
    public ResResult<Map<String, Object>> runtime() {
        Map<String, Object> data = new LinkedHashMap<>();
        String configuredEngine = ttsProperties.getPlayback() == null
                ? null : ttsProperties.getPlayback().getEngine();
        List<String> beanNames = new ArrayList<>(playbackPorts.keySet());
        List<String> implementations = playbackPorts.values().stream()
                .map(port -> port.getClass().getSimpleName())
                .distinct()
                .toList();
        data.put("configuredEngine", StringUtils.isBlank(configuredEngine) ? "esl" : configuredEngine);
        data.put("playbackBeanCount", playbackPorts.size());
        data.put("playbackBeanNames", beanNames);
        data.put("playbackImplementations", implementations);
        data.put("resolvedPlaybackPort", implementations.size() == 1 ? implementations.get(0) : null);
        data.put("uniquePlaybackBean", playbackPorts.size() == 1);
        return ResResult.success(data);
    }

    @Operation(summary = "音色列表（按服务商）", method = "GET")
    @PreAuthorize("@authz.hasPerm('ai:tts:use')")
    @GetMapping("/voices")
    public ResResult<List<TtsVoiceVo>> voices(@RequestParam(value = "provider", required = false) String provider) {
        return ResResult.success(defaultVoices(provider));
    }

    /**
     * 试听 / FreeSWITCH 读取：按 cacheKey 返回音频字节（audio/wav）。
     * 浏览器可直接 &lt;audio src=".../tts/v1/file/{cacheKey}"&gt; 播放。
     */
    @Operation(summary = "按缓存键获取音频文件（试听/FS读取）", method = "GET")
    @GetMapping("/file/{cacheKey}")
    public ResponseEntity<byte[]> file(@PathVariable("cacheKey") String cacheKey) throws IOException {
        TtsFile file = ttsBusinessService.getFileByCacheKey(cacheKey);
        if (file == null || StringUtils.isBlank(file.getFilePath())) {
            return ResponseEntity.notFound().build();
        }
        byte[] data = ttsFileManager.read(file.getFilePath());
        boolean isWav = "WAV".equalsIgnoreCase(file.getFormat()) || file.getFileName().toLowerCase().endsWith(".wav");
        MediaType type = isWav ? MediaType.valueOf("audio/wav") : MediaType.APPLICATION_OCTET_STREAM;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(type);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\""
                + (file.getFileName() == null ? "tts.wav" : file.getFileName()) + "\"");
        headers.setCacheControl("public, max-age=3600");
        return new ResponseEntity<>(data, headers, org.springframework.http.HttpStatus.OK);
    }

    /** 默认音色目录（Mock 兜底 + 常见阿里/腾讯音色）。 */
    private List<TtsVoiceVo> defaultVoices(String provider) {
        List<TtsVoiceVo> all = new ArrayList<>();
        all.add(new TtsVoiceVo("mock-default", "Mock 兜底音色", "zh", "N"));
        all.add(new TtsVoiceVo("xiaoyun", "阿里云-小云", "zh", "F"));
        all.add(new TtsVoiceVo("xiaoxia", "阿里云-小夏", "zh", "F"));
        all.add(new TtsVoiceVo("zhixiaomei", "阿里云-知小美", "zh", "F"));
        all.add(new TtsVoiceVo("101001", "腾讯云-智瑜", "zh", "F"));
        all.add(new TtsVoiceVo("101002", "腾讯云-智玲", "zh", "F"));
        all.add(new TtsVoiceVo("longxiaochun", "CosyVoice-龙小淳", "zh", "F"));
        if (StringUtils.isBlank(provider)) {
            return all;
        }
        List<TtsVoiceVo> filtered = new ArrayList<>();
        for (TtsVoiceVo v : all) {
            if (matches(v, provider)) {
                filtered.add(v);
            }
        }
        return filtered.isEmpty() ? all : filtered;
    }

    private boolean matches(TtsVoiceVo v, String provider) {
        return switch (provider.toUpperCase()) {
            case "MOCK" -> "mock-default".equals(v.getVoice());
            case "ALIYUN" -> !v.getVoice().startsWith("1") && !"mock-default".equals(v.getVoice())
                    && !"longxiaochun".equals(v.getVoice());
            case "TENCENT" -> v.getVoice().startsWith("1");
            case "COSYVOICE" -> "longxiaochun".equals(v.getVoice());
            default -> true;
        };
    }
}
