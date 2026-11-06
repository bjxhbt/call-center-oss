package com.och.system.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PhoneUtil} 单元测试（任务 6.2）。
 *
 * @author och
 */
class PhoneUtilTest {

    @Test
    void normalize_stripsSeparators() {
        assertThat(PhoneUtil.normalize("138-1234-5678")).isEqualTo("13812345678");
        assertThat(PhoneUtil.normalize(" 010 1234 5678 ")).isEqualTo("01012345678");
        assertThat(PhoneUtil.normalize("(0755)12345678")).isEqualTo("075512345678");
    }

    @Test
    void normalize_keepsInternationalPlus() {
        assertThat(PhoneUtil.normalize("+86 138 1234 5678")).isEqualTo("+8613812345678");
    }

    @Test
    void normalize_nullOrBlankReturnsNull() {
        assertThat(PhoneUtil.normalize(null)).isNull();
        assertThat(PhoneUtil.normalize("")).isNull();
        assertThat(PhoneUtil.normalize("   ")).isNull();
        assertThat(PhoneUtil.normalize("--()")).isNull();
    }

    @Test
    void same_comparesAfterNormalization() {
        assertThat(PhoneUtil.same("138-1234-5678", "13812345678")).isTrue();
        assertThat(PhoneUtil.same("13812345678", "13812345679")).isFalse();
        assertThat(PhoneUtil.same(null, null)).isTrue();
    }
}
