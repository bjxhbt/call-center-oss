package com.och.system.constant;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 统一表单校验正则单元测试（任务 10.1）。
 * 后端正则需与前端 src/consts/validate.ts 保持一致。
 *
 * @author och
 */
class ValidationConstantsTest {

    @Test
    void ipPattern_validAndInvalid() {
        assertThat(matches(ValidationConstants.IP_PATTERN, "127.0.0.1")).isTrue();
        assertThat(matches(ValidationConstants.IP_PATTERN, "127.0.0.1")).isTrue();
        assertThat(matches(ValidationConstants.IP_PATTERN, "256.1.1.1")).isFalse();
        assertThat(matches(ValidationConstants.IP_PATTERN, "192.168.1")).isFalse();
        assertThat(matches(ValidationConstants.IP_PATTERN, "abc")).isFalse();
    }

    @Test
    void portPattern_validAndInvalid() {
        assertThat(matches(ValidationConstants.PORT_PATTERN, "1")).isTrue();
        assertThat(matches(ValidationConstants.PORT_PATTERN, "5060")).isTrue();
        assertThat(matches(ValidationConstants.PORT_PATTERN, "65535")).isTrue();
        assertThat(matches(ValidationConstants.PORT_PATTERN, "0")).isFalse();
        assertThat(matches(ValidationConstants.PORT_PATTERN, "65536")).isFalse();
        assertThat(matches(ValidationConstants.PORT_PATTERN, "abc")).isFalse();
    }

    @Test
    void numberPattern_validAndInvalid() {
        assertThat(matches(ValidationConstants.NUMBER_PATTERN, "1000")).isTrue();
        assertThat(matches(ValidationConstants.NUMBER_PATTERN, "9*123")).isTrue();
        assertThat(matches(ValidationConstants.NUMBER_PATTERN, "100abc")).isFalse();
        assertThat(matches(ValidationConstants.NUMBER_PATTERN, "")).isFalse();
    }

    @Test
    void phonePattern_validAndInvalid() {
        assertThat(matches(ValidationConstants.PHONE_PATTERN, "13812345678")).isTrue();
        assertThat(matches(ValidationConstants.PHONE_PATTERN, "010-12345678")).isTrue();
        assertThat(matches(ValidationConstants.PHONE_PATTERN, "075512345678")).isTrue();
        // 短号、非法字符
        assertThat(matches(ValidationConstants.PHONE_PATTERN, "12345")).isFalse();
        assertThat(matches(ValidationConstants.PHONE_PATTERN, "138abcdef")).isFalse();
    }

    @Test
    void prefixPattern_validAndInvalid() {
        assertThat(matches(ValidationConstants.PREFIX_PATTERN, "9")).isTrue();
        assertThat(matches(ValidationConstants.PREFIX_PATTERN, "00")).isTrue();
        assertThat(matches(ValidationConstants.PREFIX_PATTERN, "9*")).isFalse();
    }

    @Test
    void namePattern_validAndInvalid() {
        assertThat(matches(ValidationConstants.NAME_PATTERN, "坐席A")).isTrue();
        assertThat(matches(ValidationConstants.NAME_PATTERN, "agent_01")).isTrue();
        assertThat(matches(ValidationConstants.NAME_PATTERN, "")).isFalse();
        assertThat(matches(ValidationConstants.NAME_PATTERN, "a b")).isFalse();
    }

    private boolean matches(String regex, String input) {
        return Pattern.matches(regex, input);
    }
}
