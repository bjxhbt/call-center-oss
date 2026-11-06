package com.och.api.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Registers the unauthenticated-by-JWT, shared-token-protected FS audio path. */
@Configuration
public class AsrAudioWebSocketConfig implements WebSocketConfigurer {

    private final AsrAudioWebSocketHandler handler;

    public AsrAudioWebSocketConfig(AsrAudioWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, AsrAudioWebSocketHandler.ENDPOINT)
                .setAllowedOriginPatterns("*");
    }
}
