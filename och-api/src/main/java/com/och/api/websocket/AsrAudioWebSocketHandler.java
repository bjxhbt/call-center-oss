package com.och.api.websocket;

import com.och.ai.asr.config.AsrProperties;
import com.och.ai.asr.ingest.AsrStreamTokenValidator;
import com.och.ai.asr.ingest.IAsrAudioIngestService;
import com.och.ai.asr.model.AudioCodec;
import com.och.ai.asr.model.CallAudioFrame;
import com.och.ai.asr.model.Speaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RFC-6455 binary PCM ingress compatible with mod_audio_fork/mod_audio_stream.
 *
 * <p>The FreeSWITCH modules send raw linear 16-bit PCM binary messages. Call
 * correlation is carried in the WebSocket query string because a media-bug
 * client cannot attach the platform JWT. An optional shared stream token is
 * still enforced.</p>
 */
@Slf4j
@Component
public class AsrAudioWebSocketHandler extends AbstractWebSocketHandler implements SubProtocolCapable {

    public static final String ENDPOINT = "/asr/v1/audio/ws";
    public static final String AUDIO_FORK_SUBPROTOCOL = "audio.drachtio.org";

    private static final String ATTR_ACCEPTED = "och.asr.ws.accepted";
    private static final String ATTR_CALL_ID = "och.asr.ws.callId";
    private static final String ATTR_UNIQUE_ID = "och.asr.ws.uniqueId";
    private static final String ATTR_CODEC = "och.asr.ws.codec";
    private static final String ATTR_SAMPLE_RATE = "och.asr.ws.sampleRate";
    private static final String ATTR_CHANNELS = "och.asr.ws.channels";
    private static final String ATTR_SPEAKER = "och.asr.ws.speaker";
    private static final String ATTR_SEQUENCE = "och.asr.ws.sequence";

    private final IAsrAudioIngestService audioIngest;
    private final AsrProperties properties;
    private final AtomicInteger activeConnections = new AtomicInteger();
    private final AtomicLong acceptedConnections = new AtomicLong();
    private final AtomicLong rejectedConnections = new AtomicLong();
    private final AtomicLong acceptedFrames = new AtomicLong();
    private final AtomicLong acceptedBytes = new AtomicLong();
    private final AtomicLong transportErrors = new AtomicLong();
    private final AtomicLong lastFrameAt = new AtomicLong();

    public AsrAudioWebSocketHandler(IAsrAudioIngestService audioIngest, AsrProperties properties) {
        this.audioIngest = audioIngest;
        this.properties = properties;
    }

    @Override
    public List<String> getSubProtocols() {
        return List.of(AUDIO_FORK_SUBPROTOCOL);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        URI uri = session.getUri();
        MultiValueMap<String, String> query = uri == null
                ? new org.springframework.util.LinkedMultiValueMap<>()
                : UriComponentsBuilder.fromUri(uri).build().getQueryParams();
        Long callId = parseLong(query.getFirst("callId"));
        String queryToken = firstText(query.getFirst("streamToken"), query.getFirst("token"));
        HttpHeaders headers = session.getHandshakeHeaders();
        String headerToken = headers == null ? null : headers.getFirst("X-Asr-Stream-Token");
        if (callId == null || !AsrStreamTokenValidator.accepts(properties, headerToken, queryToken)) {
            rejectedConnections.incrementAndGet();
            log.warn("Reject ASR audio WebSocket sessionId={} reason={}", session.getId(),
                    callId == null ? "missing-callId" : "invalid-stream-token");
            session.close(CloseStatus.POLICY_VIOLATION.withReason(
                    callId == null ? "missing callId" : "invalid stream token"));
            return;
        }

        Map<String, Object> attributes = session.getAttributes();
        attributes.put(ATTR_ACCEPTED, Boolean.TRUE);
        attributes.put(ATTR_CALL_ID, callId);
        attributes.put(ATTR_UNIQUE_ID, query.getFirst("uniqueId"));
        attributes.put(ATTR_CODEC, AudioCodec.parse(query.getFirst("codec"), AudioCodec.PCM));
        attributes.put(ATTR_SAMPLE_RATE, positiveInt(query.getFirst("sampleRate"),
                properties.getAudioStream() == null ? 8000 : properties.getAudioStream().getSampleRate()));
        attributes.put(ATTR_CHANNELS, positiveInt(query.getFirst("channels"), 1));
        attributes.put(ATTR_SPEAKER, Speaker.parse(query.getFirst("speaker")));
        attributes.put(ATTR_SEQUENCE, new AtomicLong());
        acceptedConnections.incrementAndGet();
        activeConnections.incrementAndGet();
        log.info("ASR audio WebSocket connected sessionId={} callId={} uniqueId={} sampleRate={}",
                session.getId(), callId, attributes.get(ATTR_UNIQUE_ID), attributes.get(ATTR_SAMPLE_RATE));
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        if (!Boolean.TRUE.equals(session.getAttributes().get(ATTR_ACCEPTED))) {
            return;
        }
        ByteBuffer payload = message.getPayload().slice();
        if (!payload.hasRemaining()) {
            return;
        }
        byte[] data = new byte[payload.remaining()];
        payload.get(data);
        int maxFrameBytes = Math.max(2, properties.getMaxFrameBytes());
        AudioCodec codec = (AudioCodec) session.getAttributes().get(ATTR_CODEC);
        int offset = 0;
        while (offset < data.length) {
            int length = Math.min(maxFrameBytes, data.length - offset);
            if (codec == AudioCodec.PCM && length > 1 && (length & 1) == 1 && offset + length < data.length) {
                length--;
            }
            byte[] frameData = offset == 0 && length == data.length
                    ? data
                    : Arrays.copyOfRange(data, offset, offset + length);
            ingest(session, codec, frameData);
            offset += length;
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Some module builds send initial metadata/control text. Audio ingress is
        // intentionally binary-only, but text must not tear down the call stream.
        log.debug("Ignore ASR audio WebSocket text sessionId={} bytes={}",
                session.getId(), message.getPayloadLength());
    }

    private void ingest(WebSocketSession session, AudioCodec codec, byte[] data) {
        Map<String, Object> attributes = session.getAttributes();
        AtomicLong sequence = (AtomicLong) attributes.get(ATTR_SEQUENCE);
        long now = System.currentTimeMillis();
        CallAudioFrame frame = CallAudioFrame.builder()
                .callId((Long) attributes.get(ATTR_CALL_ID))
                .uniqueId((String) attributes.get(ATTR_UNIQUE_ID))
                .codec(codec)
                .sampleRate((Integer) attributes.get(ATTR_SAMPLE_RATE))
                .channels((Integer) attributes.get(ATTR_CHANNELS))
                .data(data)
                .sequence(sequence.incrementAndGet())
                .speaker((Speaker) attributes.get(ATTR_SPEAKER))
                .timestampMs(now)
                .build();
        try {
            audioIngest.onFrame(frame);
            acceptedFrames.incrementAndGet();
            acceptedBytes.addAndGet(data.length);
            lastFrameAt.set(now);
        } catch (Exception e) {
            // Media ingest failure must never close or otherwise affect the call.
            log.warn("ASR WebSocket frame ingest failed (ignored) callId={} err={}",
                    frame.getCallId(), e.getMessage());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        transportErrors.incrementAndGet();
        log.warn("ASR audio WebSocket transport error sessionId={} err={}",
                session.getId(), exception == null ? "unknown" : exception.getMessage());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        if (Boolean.TRUE.equals(session.getAttributes().remove(ATTR_ACCEPTED))) {
            activeConnections.updateAndGet(value -> Math.max(0, value - 1));
        }
        log.info("ASR audio WebSocket closed sessionId={} status={}", session.getId(), status);
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("registered", true);
        status.put("endpoint", ENDPOINT);
        status.put("subProtocol", AUDIO_FORK_SUBPROTOCOL);
        status.put("activeConnections", activeConnections.get());
        status.put("acceptedConnections", acceptedConnections.get());
        status.put("rejectedConnections", rejectedConnections.get());
        status.put("acceptedFrames", acceptedFrames.get());
        status.put("acceptedBytes", acceptedBytes.get());
        status.put("transportErrors", transportErrors.get());
        long timestamp = lastFrameAt.get();
        status.put("lastFrameAt", timestamp == 0 ? null : timestamp);
        return status;
    }

    private static Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int positiveInt(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : fallback;
        } catch (Exception e) {
            return fallback > 0 ? fallback : 8000;
        }
    }

    private static String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }
}
