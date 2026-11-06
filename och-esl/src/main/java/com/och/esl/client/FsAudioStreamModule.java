package com.och.esl.client;

import java.util.Locale;

/**
 * Supported FreeSWITCH media-bug audio streaming modules.
 *
 * <p>Both supported modules send raw linear PCM over an RFC-6455 WebSocket.
 * Keeping the configured name, FreeSWITCH shared-library name and ESL API
 * command together prevents a configuration from probing one module while
 * invoking another command.</p>
 */
public enum FsAudioStreamModule {

    AUDIO_FORK("audio_fork", "mod_audio_fork", "uuid_audio_fork"),
    AUDIO_STREAM("audio_stream", "mod_audio_stream", "uuid_audio_stream");

    private final String configuredName;
    private final String freeSwitchModule;
    private final String apiCommand;

    FsAudioStreamModule(String configuredName, String freeSwitchModule, String apiCommand) {
        this.configuredName = configuredName;
        this.freeSwitchModule = freeSwitchModule;
        this.apiCommand = apiCommand;
    }

    public String configuredName() {
        return configuredName;
    }

    public String freeSwitchModule() {
        return freeSwitchModule;
    }

    public String apiCommand() {
        return apiCommand;
    }

    public boolean supportsEndpoint(String endpoint) {
        String transport = endpointTransport(endpoint);
        return "ws".equals(transport) || "wss".equals(transport);
    }

    public static FsAudioStreamModule resolve(String configuredName) {
        if (configuredName == null || configuredName.isBlank()) {
            return null;
        }
        String normalized = configuredName.trim().toLowerCase(Locale.ROOT);
        for (FsAudioStreamModule value : values()) {
            if (value.configuredName.equals(normalized)
                    || value.freeSwitchModule.equals(normalized)
                    || value.apiCommand.equals(normalized)) {
                return value;
            }
        }
        return null;
    }

    public static String endpointTransport(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return null;
        }
        String value = endpoint.trim();
        int separator = value.indexOf(':');
        return separator <= 0 ? "unknown" : value.substring(0, separator).toLowerCase(Locale.ROOT);
    }
}
