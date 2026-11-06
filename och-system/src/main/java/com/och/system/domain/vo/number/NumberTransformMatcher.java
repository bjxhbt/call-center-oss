package com.och.system.domain.vo.number;

import com.och.system.domain.entity.NumberTransformRule;
import org.apache.commons.lang3.StringUtils;

/**
 * 号码转换匹配与变换的纯逻辑工具类。
 * <p>
 * 不依赖 Spring 与数据库，便于单元测试覆盖前缀/正则/长度/全匹配以及去前缀、加前缀、整体替换与主叫替换。
 *
 * @author och
 */
public final class NumberTransformMatcher {

    /**
     * 匹配类型: 1-前缀 2-正则 3-长度 4-全匹配
     */
    public static final int MATCH_PREFIX = 1;
    public static final int MATCH_REGEX = 2;
    public static final int MATCH_LENGTH = 3;
    public static final int MATCH_EXACT = 4;

    private NumberTransformMatcher() {
    }

    /**
     * 判断给定号码是否命中规则(含 minLength/maxLength 长度约束)。
     */
    public static boolean matches(NumberTransformRule rule, String number) {
        if (rule == null || StringUtils.isBlank(number)) {
            return false;
        }
        if (!lengthAllowed(rule, number)) {
            return false;
        }
        Integer matchType = rule.getMatchType();
        if (matchType == null) {
            return false;
        }
        String matchValue = StringUtils.defaultString(rule.getMatchValue());
        switch (matchType) {
            case MATCH_PREFIX:
                return StringUtils.isNotBlank(matchValue) && number.startsWith(matchValue);
            case MATCH_REGEX:
                try {
                    return StringUtils.isNotBlank(matchValue) && number.matches(matchValue);
                } catch (Exception e) {
                    return false;
                }
            case MATCH_LENGTH:
                try {
                    int expect = Integer.parseInt(matchValue.trim());
                    return number.length() == expect;
                } catch (NumberFormatException e) {
                    return false;
                }
            case MATCH_EXACT:
                return StringUtils.isNotBlank(matchValue) && number.equals(matchValue);
            default:
                return false;
        }
    }

    /**
     * 对命中规则的号码执行变换，返回变换后号码(不修改 rule 对象)。
     * 若规则不匹配(含非法正则)，直接返回原号码(幂等安全，调用方无需额外守卫)。
     * 变换顺序: 先去前缀 -> 若指定整体替换则直接替换 -> 否则加前缀。
     */
    public static String apply(NumberTransformRule rule, String number) {
        if (rule == null || !matches(rule, number)) {
            return number;
        }
        String transformed = StringUtils.defaultString(number);
        String stripPrefix = StringUtils.trimToEmpty(rule.getStripPrefix());
        if (StringUtils.isNotBlank(stripPrefix) && transformed.startsWith(stripPrefix)) {
            transformed = transformed.substring(stripPrefix.length());
        }
        if (StringUtils.isNotBlank(rule.getReplaceTo())) {
            transformed = rule.getReplaceTo();
        } else if (StringUtils.isNotBlank(rule.getAddPrefix())) {
            transformed = rule.getAddPrefix() + transformed;
        }
        return transformed;
    }

    private static boolean lengthAllowed(NumberTransformRule rule, String number) {
        Integer min = rule.getMinLength();
        Integer max = rule.getMaxLength();
        if (min != null && number.length() < min) {
            return false;
        }
        if (max != null && number.length() > max) {
            return false;
        }
        return true;
    }
}
