package com.och.ai.tts;

import com.och.ai.tts.config.TtsProperties;
import com.och.ai.tts.model.TtsAudioFormat;
import com.och.ai.tts.model.TtsProviderType;
import com.och.ai.tts.model.TtsRequest;
import com.och.ai.tts.model.TtsResult;
import com.och.ai.tts.provider.MockTtsProvider;
import com.och.ai.tts.util.TtsAudioUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mock Provider 测试：无凭证即可产出可播放 WAV，验证整条链路格式正确。
 */
class MockTtsProviderTest {

    private final TtsProperties properties = new TtsProperties();
    private final MockTtsProvider provider = new MockTtsProvider(properties);

    @Test
    void synthesizeWav_validPlayableAudio() {
        TtsRequest req = TtsRequest.builder()
                .text("欢迎致电呼叫中心")
                .voice("mock-default")
                .sampleRate(8000)
                .format(TtsAudioFormat.WAV)
                .build();

        TtsResult result = provider.synthesize(req);

        assertTrue(result.isSuccess());
        assertEquals(TtsProviderType.MOCK, result.getProvider());
        assertEquals(TtsAudioFormat.WAV, result.getFormat());
        assertEquals(8000, result.getSampleRate());
        assertNotNull(result.getAudioBytes());
        assertTrue(result.getAudioBytes().length > 44);
        assertTrue(TtsAudioUtils.validateWav(result.getAudioBytes()) > 0, "必须是合法 WAV");
        assertTrue(result.getDurationMs() > 0);
        assertTrue(result.getSizeBytes() > 0);
    }

    @Test
    void synthesizePcm_rawHeaderless() {
        TtsRequest req = TtsRequest.builder()
                .text("测试").format(TtsAudioFormat.PCM).sampleRate(8000).build();
        TtsResult result = provider.synthesize(req);
        assertTrue(result.isSuccess());
        // 裸 PCM 不以 RIFF 开头
        assertNotEquals('R', (char) result.getAudioBytes()[0]);
        assertTrue(result.getAudioBytes().length % 2 == 0, "16bit PCM 字节数应为偶数");
    }

    @Test
    void longerTextYieldsLongerAudio() {
        TtsRequest shortReq = TtsRequest.builder().text("啊").format(TtsAudioFormat.WAV).sampleRate(8000).build();
        TtsRequest longReq = TtsRequest.builder()
                .text("这是一段很长的文本用来验证时长随文本增长".repeat(5))
                .format(TtsAudioFormat.WAV).sampleRate(8000).build();
        assertTrue(provider.synthesize(longReq).getDurationMs() >= provider.synthesize(shortReq).getDurationMs());
    }
}
