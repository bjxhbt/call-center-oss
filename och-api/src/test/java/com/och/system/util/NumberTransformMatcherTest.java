package com.och.system.util;

import com.och.system.domain.entity.NumberTransformRule;
import com.och.system.domain.vo.number.NumberTransformMatcher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 号码转换匹配/变换纯逻辑测试。
 * 覆盖前缀/正则/长度/全匹配、去前缀/加前缀/整体替换与 918→018 场景。
 */
class NumberTransformMatcherTest {

    private NumberTransformRule rule(int matchType, String matchValue,
                                     String strip, String add, String replaceTo) {
        NumberTransformRule r = new NumberTransformRule();
        r.setMatchType(matchType);
        r.setMatchValue(matchValue);
        r.setStripPrefix(strip);
        r.setAddPrefix(add);
        r.setReplaceTo(replaceTo);
        return r;
    }

    @Test
    void prefixMatch_thenStripAndAddPrefix_918_to_018() {
        // 918xxxxxxxxx -> 去掉接入码 "9" -> 18xxxxxxxxx -> 前置 "0" -> 018xxxxxxxxx
        NumberTransformRule r = rule(NumberTransformMatcher.MATCH_PREFIX, "9", "9", "0", null);
        assertTrue(NumberTransformMatcher.matches(r, "91800001111"));
        assertEquals("01800001111", NumberTransformMatcher.apply(r, "91800001111"));
    }

    @Test
    void prefixNotMatch_otherNumberUnchanged() {
        NumberTransformRule r = rule(NumberTransformMatcher.MATCH_PREFIX, "9", "9", "", null);
        assertFalse(NumberTransformMatcher.matches(r, "8000011"));
    }

    @Test
    void replaceTo_overridesStripAndAdd() {
        NumberTransformRule r = rule(NumberTransformMatcher.MATCH_EXACT, "10086", "9", "0", "4001234567");
        assertTrue(NumberTransformMatcher.matches(r, "10086"));
        assertEquals("4001234567", NumberTransformMatcher.apply(r, "10086"));
    }

    @Test
    void regexMatch_mobileNumber() {
        NumberTransformRule r = rule(NumberTransformMatcher.MATCH_REGEX, "^1[3-9]\\d{9}$", null, "0", null);
        assertTrue(NumberTransformMatcher.matches(r, "13800138000"));
        assertFalse(NumberTransformMatcher.matches(r, "123"));
        // 命中后加 0 前缀
        assertEquals("013800138000", NumberTransformMatcher.apply(r, "13800138000"));
    }

    @Test
    void lengthMatch_exactLength() {
        NumberTransformRule r = rule(NumberTransformMatcher.MATCH_LENGTH, "11", "0", "", null);
        assertTrue(NumberTransformMatcher.matches(r, "01380013800"));
        assertFalse(NumberTransformMatcher.matches(r, "1380013800"));
    }

    @Test
    void lengthBounds_minMaxConstraints() {
        NumberTransformRule r = rule(NumberTransformMatcher.MATCH_PREFIX, "1", null, null, null);
        r.setMinLength(11);
        r.setMaxLength(11);
        assertTrue(NumberTransformMatcher.matches(r, "13800138000"));
        assertFalse(NumberTransformMatcher.matches(r, "123")); // 长度不足
        r.setMinLength(null);
        r.setMaxLength(5);
        assertFalse(NumberTransformMatcher.matches(r, "13800138000")); // 超长
    }

    @Test
    void invalidRegex_doesNotMatch_andKeepsNumber() {
        NumberTransformRule r = rule(NumberTransformMatcher.MATCH_REGEX, "([0-9", null, "0", null);
        assertFalse(NumberTransformMatcher.matches(r, "13800138000"));
        assertEquals("13800138000", NumberTransformMatcher.apply(r, "13800138000"));
    }

    @Test
    void nullOrBlankInputs_doNotMatch() {
        NumberTransformRule r = rule(NumberTransformMatcher.MATCH_PREFIX, "9", "9", "", null);
        assertFalse(NumberTransformMatcher.matches(r, null));
        assertFalse(NumberTransformMatcher.matches(r, ""));
        assertFalse(NumberTransformMatcher.matches(null, "911"));
    }
}
