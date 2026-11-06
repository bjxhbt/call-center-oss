package com.och.system;

import cn.hutool.core.util.IdUtil;
import com.och.ai.tts.playback.TtsPlaybackPort;
import com.och.api.adapter.FsTtsPlaybackAdapter;
import com.och.ai.service.IDashScopeService;
import com.och.api.OchApiApplication;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;

/**
 * Full Spring context smoke test for {@link OchApiApplication}.
 *
 * <p>Requires local infrastructure (at least Redis 127.0.0.1:6379). When Redis is
 * unreachable the entire class is skipped so the reactor stays green in pure unit-test CI.</p>
 *
 * <p>Enable with Redis up: run the suite normally. Force-skip always: leave Redis down.
 * Cloud DashScope chat remains {@code @Disabled} until a real api-key is configured.</p>
 */
@EnabledIf("com.och.system.SystemApplicationTests#infraAvailable")
@SpringBootTest(classes = OchApiApplication.class)
class SystemApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private IDashScopeService dashScopeService;

    /**
     * JUnit condition: only load full context when Redis accepts TCP connections.
     * Evaluated before SpringBootTest context bootstrap.
     */
    static boolean infraAvailable() {
        return isPortOpen("127.0.0.1", 6379, 300);
    }

    private static boolean isPortOpen(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Context load smoke: key beans must be wired when infra is available.
     */
    @Test
    void contextLoads() {
        Assertions.assertNotNull(applicationContext, "Spring ApplicationContext missing");
        Assertions.assertNotNull(publisher, "ApplicationEventPublisher missing");
        Assertions.assertNotNull(dashScopeService, "IDashScopeService missing");
        Map<String, TtsPlaybackPort> playbackPorts = applicationContext.getBeansOfType(TtsPlaybackPort.class);
        Assertions.assertEquals(1, playbackPorts.size(), "Exactly one TtsPlaybackPort must be active");
        Assertions.assertInstanceOf(FsTtsPlaybackAdapter.class, playbackPorts.values().iterator().next(),
                "Default runtime must use the production FreeSWITCH playback adapter");
    }

    /**
     * Manual DashScope chat probe. Disabled by default (needs real api-key).
     */
    @Disabled("Needs real DashScope api-key; placeholder keys cause HTTP 401")
    @Test
    void dashScopeChatManual() {
        String chatId = IdUtil.fastSimpleUUID();
        String chat = dashScopeService.chat(chatId, "hello");
        System.out.println(chat);
        String str = dashScopeService.chat(chatId, "write a short poem");
        System.out.println(str);
        String str1 = dashScopeService.chat(chatId, "repeat previous question");
        System.out.println(str1);
        String str2 = dashScopeService.chat(chatId, "yes");
        System.out.println(str2);
    }
}
