package com.och.system.constant;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 错误码单元测试（任务 10.2）：确保码值唯一、分组前缀正确、消息非空。
 *
 * @author och
 */
class ErrorCodeTest {

    @Test
    void codesAreUnique() {
        Set<Integer> codes = new HashSet<>();
        for (ErrorCode ec : ErrorCode.values()) {
            assertThat(codes.add(ec.getCode()))
                    .as("错误码重复: %s (%d)", ec.name(), ec.getCode())
                    .isTrue();
        }
    }

    @Test
    void messagesAreNonBlank() {
        for (ErrorCode ec : ErrorCode.values()) {
            assertThat(ec.getMessage()).as("消息为空: %s", ec.name()).isNotBlank();
        }
    }

    @Test
    void categoryGroupingByPrefix() {
        // 11xxx 坐席业务、12xxx 客户业务、22xxx ASR、23xxx TTS 各自有代表项
        assertThat(ErrorCode.AGENT_SIP_BIND_REPEAT.getCode()).isBetween(11000, 11999);
        assertThat(ErrorCode.CUSTOMER_PHONE_REPEAT.getCode()).isBetween(12000, 12999);
        assertThat(ErrorCode.ASR_SERVICE_FAIL.getCode()).isBetween(22000, 22999);
        assertThat(ErrorCode.TTS_SERVICE_FAIL.getCode()).isBetween(23000, 23999);
    }

    @Test
    void coversKeyBusinessScenarios() {
        // 关键场景的错误码均存在
        assertThat(Arrays.stream(ErrorCode.values())
                .anyMatch(e -> e.getMessage().contains("SIP账号"))).isTrue();
        assertThat(Arrays.stream(ErrorCode.values())
                .anyMatch(e -> e.getMessage().contains("电话号码"))).isTrue();
    }
}
