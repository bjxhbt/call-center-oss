package com.och.esl.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FsAudioStreamModuleTest {

    @Test
    void resolvesConfiguredModuleAndMatchingApiCommand() {
        assertEquals(FsAudioStreamModule.AUDIO_FORK,
                FsAudioStreamModule.resolve("mod_audio_fork"));
        assertEquals("uuid_audio_fork", FsAudioStreamModule.AUDIO_FORK.apiCommand());
        assertEquals(FsAudioStreamModule.AUDIO_STREAM,
                FsAudioStreamModule.resolve("uuid_audio_stream"));
        assertEquals("mod_audio_stream", FsAudioStreamModule.AUDIO_STREAM.freeSwitchModule());
        assertNull(FsAudioStreamModule.resolve("unknown"));
    }

    @Test
    void nativeModulesRequireWebSocketEndpoint() {
        assertTrue(FsAudioStreamModule.AUDIO_FORK.supportsEndpoint("ws://127.0.0.1/audio"));
        assertTrue(FsAudioStreamModule.AUDIO_STREAM.supportsEndpoint("wss://example.test/audio"));
        assertFalse(FsAudioStreamModule.AUDIO_FORK.supportsEndpoint("http://127.0.0.1/frame"));
        assertEquals("http", FsAudioStreamModule.endpointTransport("http://127.0.0.1/frame"));
    }
}
