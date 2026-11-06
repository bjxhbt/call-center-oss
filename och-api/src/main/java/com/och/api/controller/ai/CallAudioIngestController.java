package com.och.api.controller.ai;

import com.och.ai.asr.config.AsrProperties;
import com.och.ai.asr.ingest.IAsrAudioIngestService;
import com.och.ai.asr.ingest.AsrStreamTokenValidator;
import com.och.ai.asr.model.AudioCodec;
import com.och.ai.asr.model.CallAudioFrame;
import com.och.ai.asr.model.Speaker;
import com.och.common.base.BaseController;
import com.och.common.base.ResResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Call audio ingest endpoint (plan 11.2 P0 FreeSWITCH real audio).
 *
 * <p>FreeSWITCH mod_audio_fork / custom HTTP adapter POSTs binary PCM frames here.
 * Auth: permitAll at SecurityFilterChain for /asr/v1/audio/** (FS cannot use JWT).
 * callId/uniqueId/codec/sampleRate may come from Headers (X-Call-Id etc.) or query params
 * (easier for FreeSWITCH Lua/HTTP adapters). Optional shared secret: Header X-Asr-Stream-Token or query streamToken when
 * och.asr.audio-stream.stream-token is configured; empty token disables check (intranet).</p>
 *
 * <p>Failures always return 2xx so FS does not tear down media/call.</p>
 */
@Slf4j
@Tag(name = "ASR音频接入")
@RestController
@RequestMapping("asr/v1/audio")
public class CallAudioIngestController extends BaseController {

    @Autowired
    private IAsrAudioIngestService audioIngest;

    @Autowired
    private AsrProperties asrProperties;

    @Operation(summary = "二进制音频帧接入(mod_audio_fork/HTTP适配)", method = "POST")
    @PostMapping("/frame")
    public ResResult<String> frame(
            @RequestHeader(value = "X-Call-Id", required = false) Long callIdHeader,
            @RequestHeader(value = "X-Unique-Id", required = false) String uniqueIdHeader,
            @RequestHeader(value = "X-Codec", required = false) String codecHeader,
            @RequestHeader(value = "X-Sample-Rate", required = false) Integer sampleRateHeader,
            @RequestHeader(value = "X-Sequence", required = false) Long sequenceHeader,
            @RequestHeader(value = "X-Speaker", required = false) String speakerHeader,
            @RequestHeader(value = "X-Asr-Stream-Token", required = false) String streamTokenHeader,
            @RequestParam(value = "callId", required = false) Long callIdQuery,
            @RequestParam(value = "uniqueId", required = false) String uniqueIdQuery,
            @RequestParam(value = "codec", required = false) String codecQuery,
            @RequestParam(value = "sampleRate", required = false) Integer sampleRateQuery,
            @RequestParam(value = "sequence", required = false) Long sequenceQuery,
            @RequestParam(value = "speaker", required = false) String speakerQuery,
            @RequestParam(value = "streamToken", required = false) String streamTokenQuery,
            @RequestBody(required = false) byte[] data) {
        Long callId = callIdHeader != null ? callIdHeader : callIdQuery;
        String uniqueId = StringUtils.hasText(uniqueIdHeader) ? uniqueIdHeader : uniqueIdQuery;
        String codec = StringUtils.hasText(codecHeader) ? codecHeader
                : (StringUtils.hasText(codecQuery) ? codecQuery : "PCMA");
        Integer sampleRate = sampleRateHeader != null ? sampleRateHeader
                : (sampleRateQuery != null ? sampleRateQuery : 8000);
        Long sequence = sequenceHeader != null ? sequenceHeader : sequenceQuery;
        String speaker = StringUtils.hasText(speakerHeader) ? speakerHeader : speakerQuery;
        if (!acceptStreamToken(streamTokenHeader, streamTokenQuery)) {
            // still 2xx to avoid FS hangup on auth misconfig during media
            log.warn("ASR audio frame rejected: invalid stream token callId:{}", callId);
            return success("token-rejected");
        }
        if (callId == null || data == null || data.length == 0) {
            return success("no-callId-or-data");
        }
        try {
            CallAudioFrame frame = CallAudioFrame.builder()
                    .callId(callId)
                    .uniqueId(uniqueId)
                    .codec(AudioCodec.parse(codec, AudioCodec.PCMA))
                    .sampleRate(sampleRate)
                    .channels(1)
                    .data(data)
                    .sequence(sequence)
                    .speaker(Speaker.parse(speaker))
                    .timestampMs(System.currentTimeMillis())
                    .build();
            audioIngest.onFrame(frame);
            return success("ok");
        } catch (Exception e) {
            log.warn("audio frame ingest error (ignored) callId:{} err:{}", callId, e.getMessage());
            return success("error-ignored:" + e.getMessage());
        }
    }

    @Operation(summary = "Base64音频帧接入", method = "POST")
    @PostMapping("/frame-base64")
    public ResResult<String> frameBase64(
            @RequestHeader(value = "X-Asr-Stream-Token", required = false) String streamTokenHeader,
            @RequestParam(value = "streamToken", required = false) String streamTokenQuery,
            @RequestBody FrameBase64Request req) {
        if (!acceptStreamToken(streamTokenHeader, streamTokenQuery)) {
            log.warn("ASR base64 frame rejected: invalid stream token");
            return success("token-rejected");
        }
        if (req == null || req.getCallId() == null || req.getBase64Data() == null) {
            return success("no-callId-or-data");
        }
        try {
            byte[] data = java.util.Base64.getDecoder().decode(req.getBase64Data());
            CallAudioFrame frame = CallAudioFrame.builder()
                    .callId(req.getCallId())
                    .uniqueId(req.getUniqueId())
                    .codec(AudioCodec.parse(req.getCodec(), AudioCodec.PCM))
                    .sampleRate(req.getSampleRate() == null ? 16000 : req.getSampleRate())
                    .channels(1)
                    .data(data)
                    .sequence(req.getSequence())
                    .speaker(Speaker.parse(req.getSpeaker()))
                    .timestampMs(System.currentTimeMillis())
                    .build();
            audioIngest.onFrame(frame);
            return success("ok");
        } catch (Exception e) {
            log.warn("base64 audio frame error (ignored) err:{}", e.getMessage());
            return success("error-ignored:" + e.getMessage());
        }
    }

    /**
     * When och.asr.audio-stream.stream-token is empty, accept all (intranet deploy).
     * Otherwise require matching Header or query param.
     */
    boolean acceptStreamToken(String headerToken, String queryToken) {
        return AsrStreamTokenValidator.accepts(asrProperties, headerToken, queryToken);
    }

    @Data
    public static class FrameBase64Request {
        private Long callId;
        private String uniqueId;
        private String codec;
        private Integer sampleRate;
        private Long sequence;
        private String speaker;
        private String base64Data;
    }
}
