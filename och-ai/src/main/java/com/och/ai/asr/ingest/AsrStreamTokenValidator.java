package com.och.ai.asr.ingest;

import com.och.ai.asr.config.AsrProperties;
import org.springframework.util.StringUtils;

/** Shared-token validation used by both HTTP and WebSocket audio ingress. */
public final class AsrStreamTokenValidator {

    private AsrStreamTokenValidator() {
    }

    public static boolean accepts(AsrProperties properties, String headerToken, String queryToken) {
        AsrProperties.AudioStream config = properties == null ? null : properties.getAudioStream();
        if (config == null || !StringUtils.hasText(config.getStreamToken())) {
            return true;
        }
        String expected = config.getStreamToken().trim();
        return (StringUtils.hasText(headerToken) && expected.equals(headerToken.trim()))
                || (StringUtils.hasText(queryToken) && expected.equals(queryToken.trim()));
    }
}
