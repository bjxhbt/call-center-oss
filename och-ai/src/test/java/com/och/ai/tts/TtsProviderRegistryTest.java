package com.och.ai.tts;

import com.och.ai.tts.config.TtsProperties;
import com.och.ai.tts.model.TtsProviderType;
import com.och.ai.tts.model.TtsRequest;
import com.och.ai.tts.model.TtsResult;
import com.och.ai.tts.provider.MockTtsProvider;
import com.och.ai.tts.provider.TtsProviderRegistry;
import com.och.ai.tts.provider.TtsServiceProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Provider 注册表测试：主用缺失/请求不可用时回退 MOCK（降级，方案 8.10）。
 */
class TtsProviderRegistryTest {

    @Test
    void fallbacksToMockWhenPrimaryMissing() {
        TtsProperties props = new TtsProperties(); // provider 默认 MOCK
        MockTtsProvider mock = new MockTtsProvider(props);
        com.och.ai.tts.provider.TtsProviderRegistry registry =
                new com.och.ai.tts.provider.TtsProviderRegistry(List.of(mock), props);
        registry.init();

        // 请求一个未注册的 ALIYUN，应回退 MOCK
        TtsServiceProvider selected = registry.select(TtsProviderType.ALIYUN);
        assertSame(mock, selected);
        assertEquals(TtsProviderType.MOCK, registry.resolveType(TtsProviderType.ALIYUN));
        assertFalse(registry.isAvailable(TtsProviderType.ALIYUN));
        assertTrue(registry.isAvailable(TtsProviderType.MOCK));
    }

    @Test
    void usesRequestedWhenAvailable() {
        TtsProperties props = new TtsProperties();
        props.setProvider(TtsProviderType.ALIYUN);
        MockTtsProvider mock = new MockTtsProvider(props);
        TtsServiceProvider fakeAliyun = new TtsServiceProvider() {
            @Override
            public TtsProviderType type() {
                return TtsProviderType.ALIYUN;
            }

            @Override
            public TtsResult synthesize(TtsRequest request) {
                return TtsResult.builder().success(true).provider(TtsProviderType.ALIYUN).build();
            }
        };
        com.och.ai.tts.provider.TtsProviderRegistry registry =
                new com.och.ai.tts.provider.TtsProviderRegistry(List.of(mock, fakeAliyun), props);
        registry.init();

        assertSame(fakeAliyun, registry.select(TtsProviderType.ALIYUN));
        assertSame(fakeAliyun, registry.select(null)); // null → 主用配置 ALIYUN
    }

    @Test
    void distinguishesRegisteredProviderFromCredentialReadyProvider() {
        TtsProperties props = new TtsProperties();
        TtsServiceProvider configuredButCredentialless = new TtsServiceProvider() {
            @Override
            public TtsProviderType type() {
                return TtsProviderType.TENCENT;
            }

            @Override
            public boolean ready() {
                return false;
            }

            @Override
            public TtsResult synthesize(TtsRequest request) {
                return TtsResult.builder().success(false).provider(TtsProviderType.TENCENT).build();
            }
        };
        TtsProviderRegistry registry = new TtsProviderRegistry(
                List.of(new MockTtsProvider(props), configuredButCredentialless), props);
        registry.init();

        assertTrue(registry.isAvailable(TtsProviderType.TENCENT));
        assertFalse(registry.isReady(TtsProviderType.TENCENT));
        assertTrue(registry.isReady(TtsProviderType.MOCK));
    }
}
