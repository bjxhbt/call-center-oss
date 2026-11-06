package com.och.ai.asr.lifecycle;

import com.och.ai.asr.config.AsrProperties;
import com.och.esl.client.FsAudioStreamModule;
import com.och.esl.client.FsClient;
import com.och.esl.event.CallBridgeEvent;
import com.och.esl.event.CallHangupEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * FreeSWITCH real audio stream control (plan 7.2 / 11.2 P0).
 *
 * <p>After bridge, send ESL uuid_audio_fork start to push channel PCM to
 * och.asr.audio-stream.endpoint; on hangup send stop. Exceptions are logged only.</p>
 */
@Slf4j
@Component
public class AsrFsAudioStreamService {

    private final AsrProperties properties;
    private final FsClient fsClient;

    public AsrFsAudioStreamService(AsrProperties properties, @Lazy FsClient fsClient) {
        this.properties = properties;
        this.fsClient = fsClient;
    }

    public void startOnBridge(CallBridgeEvent event) {
        if (event == null || event.getCallId() == null) {
            return;
        }
        if (!isStreamEnabled()) {
            log.debug("ASR audio stream disabled, skip callId:{}", event.getCallId());
            return;
        }
        try {
            String address = resolveAddress();
            if (!StringUtils.hasText(address)) {
                log.warn("ASR audio stream skip: no FreeSWITCH connection callId:{}", event.getCallId());
                return;
            }
            AsrProperties.AudioStream cfg = properties.getAudioStream();
            String url = buildStreamUrl(cfg.getEndpoint(), event.getCallId(), event.getUniqueId());
            if (!StringUtils.hasText(url)) {
                log.warn("ASR audio stream skip: empty endpoint callId:{}", event.getCallId());
                return;
            }
            boolean ok = fsClient.startAudioStream(address, event.getCallId(), event.getUniqueId(),
                    cfg.getModule(), url, cfg.getSampleRate(), cfg.getMixType());
            log.info("ASR audio stream start callId:{} uuid:{} module:{} ok:{}",
                    event.getCallId(), event.getUniqueId(), cfg.getModule(), ok);
            if (cfg.isBothLegs() && StringUtils.hasText(event.getOtherUniqueId())
                    && !event.getOtherUniqueId().equals(event.getUniqueId())) {
                String otherUrl = buildStreamUrl(cfg.getEndpoint(), event.getCallId(), event.getOtherUniqueId());
                boolean ok2 = fsClient.startAudioStream(address, event.getCallId(), event.getOtherUniqueId(),
                        cfg.getModule(), otherUrl, cfg.getSampleRate(), cfg.getMixType());
                log.info("ASR other-leg stream start callId:{} otherUuid:{} ok:{}",
                        event.getCallId(), event.getOtherUniqueId(), ok2);
            }
        } catch (Exception e) {
            log.error("ASR start audio stream error (ignored) callId:{} err:{}",
                    event.getCallId(), e.getMessage(), e);
        }
    }

    public void stopOnHangup(CallHangupEvent event) {
        if (event == null || event.getCallId() == null) {
            return;
        }
        if (!isStreamEnabled()) {
            return;
        }
        try {
            String address = resolveAddress();
            if (!StringUtils.hasText(address)) {
                return;
            }
            if (StringUtils.hasText(event.getUniqueId())) {
                fsClient.stopAudioStream(address, event.getCallId(), event.getUniqueId(),
                        properties.getAudioStream().getModule());
            }
        } catch (Exception e) {
            log.error("ASR stop audio stream error (ignored) callId:{} err:{}",
                    event.getCallId(), e.getMessage());
        }
    }

    boolean isStreamEnabled() {
        if (!properties.isEnabled()) {
            return false;
        }
        AsrProperties.AudioStream cfg = properties.getAudioStream();
        if (cfg == null || !cfg.isEnabled()) {
            return false;
        }
        FsAudioStreamModule module = FsAudioStreamModule.resolve(cfg.getModule());
        if (module == null) {
            return false;
        }
        return module.supportsEndpoint(cfg.getEndpoint());
    }

    String buildStreamUrl(String endpoint, Long callId, String uniqueId) {
        if (!StringUtils.hasText(endpoint)) {
            return null;
        }
        String callIdValue = callId == null ? "" : String.valueOf(callId);
        String uniqueIdValue = uniqueId == null ? "" : uniqueId;
        String url = endpoint.trim()
                .replace("{callId}", encode(callIdValue))
                .replace("{uniqueId}", encode(uniqueIdValue));
        if (callId != null && !hasQueryParameter(url, "callId")) {
            url = appendQueryParameter(url, "callId", callIdValue);
        }
        if (StringUtils.hasText(uniqueId) && !hasQueryParameter(url, "uniqueId")) {
            url = appendQueryParameter(url, "uniqueId", uniqueIdValue);
        }
        AsrProperties.AudioStream cfg = properties.getAudioStream();
        if (cfg != null && StringUtils.hasText(cfg.getStreamToken())
                && !hasQueryParameter(url, "streamToken") && !hasQueryParameter(url, "token")) {
            url = appendQueryParameter(url, "streamToken", cfg.getStreamToken().trim());
        }
        return url;
    }

    private static String appendQueryParameter(String url, String name, String value) {
        String separator = url.indexOf('?') >= 0 ? "&" : "?";
        return url + separator + name + "=" + encode(value);
    }

    private static boolean hasQueryParameter(String url, String name) {
        return url.matches(".*[?&]" + java.util.regex.Pattern.quote(name) + "=[^&]*.*");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String resolveAddress() {
        try {
            return fsClient.anyAddress();
        } catch (Exception e) {
            log.warn("resolve FreeSWITCH address failed: {}", e.getMessage());
            return null;
        }
    }
}
