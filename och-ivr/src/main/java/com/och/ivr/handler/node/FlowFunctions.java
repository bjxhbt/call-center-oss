package com.och.ivr.handler.node;

import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IVR 内置函数(对应原系统 date_to_string 等)。
 * 支持:
 *  - date_to_string(sysdate(),"yyyyMMdd")
 *  - date_to_string(sysdate(),"HHmmss")
 *  - date_to_string(sysdate(),"E") -> 中文星期(星期一..星期日)
 * Java 模式字母与原系统 SimpleDateFormat 兼容；E 特殊处理为中文星期。
 */
public final class FlowFunctions {

    private static final String[] WEEK_CN = {"星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六"};

    // date_to_string( sysdate() , "fmt" )
    private static final Pattern DATE_TO_STR = Pattern.compile(
            "date_to_string\\s*\\(\\s*sysdate\\s*\\(\\s*\\)\\s*,\\s*\"([^\"]*)\"\\s*\\)");

    private FlowFunctions() {
    }

    /** 若表达式是 date_to_string 形式则返回格式化结果，否则返回 null */
    public static String dateToString(String expr, Date date) {
        if (expr == null) {
            return null;
        }
        Matcher m = DATE_TO_STR.matcher(expr.trim());
        if (m.matches()) {
            String fmt = m.group(1);
            return format(date, fmt);
        }
        return null;
    }

    public static String format(Date date, String fmt) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(toJavaPattern(fmt));
        String result = sdf.format(date);
        // E -> 中文星期
        if (fmt != null && fmt.equals("E")) {
            int day = date.getDay(); // 0=Sunday
            result = WEEK_CN[day];
        }
        return result;
    }

    private static String toJavaPattern(String fmt) {
        if (fmt == null) return "yyyyMMdd";
        // 原系统使用与 SimpleDateFormat 一致的字母，直接返回
        return fmt;
    }
}
