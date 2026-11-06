package com.och.system.util;

import java.util.Objects;

/**
 * 电话号码规范化工具（任务 6.2）。
 *
 * <p>统一对客户电话做去格式化（去空格/横线/括号），保证入库与重复检查口径一致；
 * 国际号码保留前导 '+', 其余只保留数字。来电弹屏按规范化号码查询客户。</p>
 *
 * @author och
 */
public final class PhoneUtil {

    private PhoneUtil() {
    }

    /**
     * 规范化电话号码：去除空格、横线、括号等分隔符，保留前导 '+' 与数字。
     *
     * @param phone 原始号码，允许为 null
     * @return 规范化后的号码；入参为 null/空白时返回 null
     */
    public static String normalize(String phone) {
        if (phone == null) {
            return null;
        }
        String trimmed = phone.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        boolean international = trimmed.startsWith("+");
        StringBuilder sb = new StringBuilder(international ? 16 : trimmed.length());
        if (international) {
            sb.append('+');
        }
        for (int i = international ? 1 : 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(c);
            }
        }
        String result = sb.toString();
        return result.isEmpty() || (international && result.length() == 1) ? null : result;
    }

    /**
     * 判断两个号码规范化后是否相等。
     */
    public static boolean same(String a, String b) {
        return Objects.equals(normalize(a), normalize(b));
    }
}
