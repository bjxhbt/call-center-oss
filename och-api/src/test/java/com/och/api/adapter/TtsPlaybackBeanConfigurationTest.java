package com.och.api.adapter;

import com.och.ai.tts.config.TtsProperties;
import com.och.ai.tts.playback.EslTtsPlaybackPort;
import com.och.ai.tts.playback.NoopTtsPlaybackPort;
import com.och.ai.tts.playback.TtsPlaybackPort;
import com.och.ai.tts.service.TtsBusinessService;
import com.och.api.service.IFsPlaybackService;
import com.och.esl.client.FsClient;
import com.och.esl.service.IFsCallCacheService;
import com.och.esl.service.IFsEslEventService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Isolated Spring context proof that every supported playback engine resolves to exactly one port.
 * This test does not require Redis, KingbaseES or FreeSWITCH.
 */
class TtsPlaybackBeanConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withPropertyValues("spring.profiles.active=tts-playback-bean-test")
            .withUserConfiguration(PlaybackBeans.class);

    @Test
    void defaultEngineHasOnlyProductionAdapter() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(TtsPlaybackPort.class);
            assertThat(context.getBean(TtsPlaybackPort.class)).isInstanceOf(FsTtsPlaybackAdapter.class);
        });
    }

    @Test
    void eslEngineHasOnlyProductionAdapter() {
        runner.withPropertyValues("och.tts.playback.engine=esl").run(context -> {
            assertThat(context).hasSingleBean(TtsPlaybackPort.class);
            assertThat(context.getBean(TtsPlaybackPort.class)).isInstanceOf(FsTtsPlaybackAdapter.class);
        });
    }

    @Test
    void noopEngineHasOnlyNoopPort() {
        runner.withPropertyValues("och.tts.playback.engine=noop").run(context -> {
            assertThat(context).hasSingleBean(TtsPlaybackPort.class);
            assertThat(context.getBean(TtsPlaybackPort.class)).isInstanceOf(NoopTtsPlaybackPort.class);
        });
    }

    @Test
    void directEslEngineHasOnlyDirectPort() {
        runner.withPropertyValues("och.tts.playback.engine=direct-esl").run(context -> {
            assertThat(context).hasSingleBean(TtsPlaybackPort.class);
            assertThat(context.getBean(TtsPlaybackPort.class)).isInstanceOf(EslTtsPlaybackPort.class);
        });
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Profile("tts-playback-bean-test")
    @Import({FsTtsPlaybackAdapter.class, EslTtsPlaybackPort.class, NoopTtsPlaybackPort.class})
    static class PlaybackBeans {

        @Bean
        IFsPlaybackService fsPlaybackService() {
            return mock(IFsPlaybackService.class);
        }

        @Bean
        TtsBusinessService ttsBusinessService() {
            return mock(TtsBusinessService.class);
        }

        @Bean
        FsClient fsClient() {
            return mock(FsClient.class);
        }

        @Bean
        IFsEslEventService fsEslEventService() {
            return mock(IFsEslEventService.class);
        }

        @Bean
        IFsCallCacheService fsCallCacheService() {
            return mock(IFsCallCacheService.class);
        }

        @Bean
        TtsProperties ttsProperties() {
            return new TtsProperties();
        }
    }
}
