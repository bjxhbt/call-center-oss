package com.och.api.controller.ai;

import com.och.ai.tts.config.TtsProperties;
import com.och.ai.tts.file.TtsFileManager;
import com.och.ai.tts.playback.NoopTtsPlaybackPort;
import com.och.ai.tts.playback.TtsPlaybackPort;
import com.och.ai.tts.provider.TtsProviderRegistry;
import com.och.ai.tts.service.TtsBusinessService;
import com.och.api.adapter.FsTtsPlaybackAdapter;
import com.och.common.base.ResResult;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TtsControllerRuntimeTest {

    @Test
    void runtimeReportsTheSingleProductionPlaybackPort() {
        Map<String, TtsPlaybackPort> ports = new LinkedHashMap<>();
        ports.put("fsTtsPlaybackAdapter", new FsTtsPlaybackAdapter());

        ResResult<Map<String, Object>> result = controller(ports).runtime();

        assertEquals(200, result.getCode());
        assertEquals("esl", result.getData().get("configuredEngine"));
        assertEquals(1, result.getData().get("playbackBeanCount"));
        assertEquals("FsTtsPlaybackAdapter", result.getData().get("resolvedPlaybackPort"));
        assertEquals(true, result.getData().get("uniquePlaybackBean"));
    }

    @Test
    void runtimeDoesNotClaimUniqueWhenMoreThanOnePortIsPresent() {
        Map<String, TtsPlaybackPort> ports = new LinkedHashMap<>();
        ports.put("fsTtsPlaybackAdapter", new FsTtsPlaybackAdapter());
        ports.put("noopTtsPlaybackPort", new NoopTtsPlaybackPort());

        ResResult<Map<String, Object>> result = controller(ports).runtime();

        assertEquals(2, result.getData().get("playbackBeanCount"));
        assertNull(result.getData().get("resolvedPlaybackPort"));
        assertFalse((Boolean) result.getData().get("uniquePlaybackBean"));
    }

    @Test
    void providersExposeRegisteredAndCredentialReadinessSeparately() {
        TtsProviderRegistry registry = mock(TtsProviderRegistry.class);
        for (com.och.ai.tts.model.TtsProviderType type : com.och.ai.tts.model.TtsProviderType.values()) {
            when(registry.isAvailable(type)).thenReturn(false);
            when(registry.isReady(type)).thenReturn(false);
        }
        when(registry.isAvailable(com.och.ai.tts.model.TtsProviderType.TENCENT)).thenReturn(true);

        ResResult<List<Map<String, Object>>> result = controller(new LinkedHashMap<>(), registry).providers();
        Map<String, Object> tencent = result.getData().stream()
                .filter(item -> "TENCENT".equals(item.get("provider")))
                .findFirst()
                .orElseThrow();

        assertEquals(true, tencent.get("available"));
        assertEquals(false, tencent.get("ready"));
    }

    private TtsController controller(Map<String, TtsPlaybackPort> ports) {
        return controller(ports, mock(TtsProviderRegistry.class));
    }

    private TtsController controller(Map<String, TtsPlaybackPort> ports, TtsProviderRegistry registry) {
        return new TtsController(
                mock(TtsBusinessService.class),
                registry,
                mock(TtsFileManager.class),
                new TtsProperties(),
                ports);
    }
}
