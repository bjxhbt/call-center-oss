package com.och.ivr.handler.node;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 轻量 IVR 表达式求值器。
 * 支持：变量引用(裸标识符 或 ${var})、字符串字面量(双引号)、数字字面量；
 * 比较 == != > >= < <= ；字符串包含 contains ；逻辑 && || ；括号。
 * 用于 assign/condition 节点(原系统表达式语法的子集)。
 */
public final class FlowExpression {

    private FlowExpression() {
    }

    /** 将变量占位 ${var} 替换为字面量值 */
    private static String substitute(String expr, Map<String, String> vars) {
        if (expr == null) {
            return "";
        }
        Pattern p = Pattern.compile("\\$\\{([^}]+)}");
        Matcher m = p.matcher(expr);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String name = m.group(1).trim();
            String val = vars == null ? null : vars.get(name);
            m.appendReplacement(sb, Matcher.quoteReplacement(val == null ? "" : val));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static boolean evalBool(String expr, Map<String, String> vars) {
        if (expr == null || expr.trim().isEmpty()) {
            return true; // 空表达式视为默认分支恒真
        }
        // 先做变量替换(${var})
        String e = substitute(expr, vars);
        // || 优先级最低
        String[] orParts = splitTopLevel(e, "||");
        for (String orPart : orParts) {
            if (evalAnd(orPart.trim(), vars)) {
                return true;
            }
        }
        return false;
    }

    private static boolean evalAnd(String e, Map<String, String> vars) {
        String[] andParts = splitTopLevel(e, "&&");
        for (String andPart : andParts) {
            if (!evalComparison(andPart.trim(), vars)) {
                return false;
            }
        }
        return true;
    }

    private static boolean evalComparison(String e, Map<String, String> vars) {
        e = e.trim();
        if (e.isEmpty()) {
            return true;
        }
        // 去掉最外层括号
        while (e.startsWith("(") && e.endsWith(")") && balanced(e.substring(1, e.length() - 1))) {
            e = e.substring(1, e.length() - 1).trim();
        }
        if (e.contains("&&") || e.contains("||")) {
            return evalBool(e, vars);
        }
        // contains
        int idx = indexOfKeyword(e, "contains");
        if (idx >= 0) {
            String left = e.substring(0, idx).trim();
            String right = e.substring(idx + "contains".length()).trim();
            String lv = valueOf(left, vars);
            String rv = valueOf(right, vars);
            return lv != null && rv != null && lv.contains(rv);
        }
        String[] ops = {"==", "!=", ">=", "<=", ">", "<"};
        for (String op : ops) {
            int pos = indexOfTopLevel(e, op);
            if (pos > 0) {
                String left = e.substring(0, pos).trim();
                String right = e.substring(pos + op.length()).trim();
                return compare(left, right, op, vars);
            }
        }
        // 没有比较符：非空字符串为真
        String v = valueOf(e, vars);
        return v != null && !v.isEmpty() && !"0".equals(v) && !"false".equalsIgnoreCase(v);
    }

    private static boolean compare(String left, String right, String op, Map<String, String> vars) {
        String lv = valueOf(left, vars);
        String rv = valueOf(right, vars);
        if (lv == null) lv = "";
        if (rv == null) rv = "";
        switch (op) {
            case "==":
                return lv.equals(rv);
            case "!=":
                return !lv.equals(rv);
            default: {
                // 数值比较，无法解析时退化为字符串比较
                try {
                    double ld = Double.parseDouble(lv.trim());
                    double rd = Double.parseDouble(rv.trim());
                    switch (op) {
                        case ">": return ld > rd;
                        case ">=": return ld >= rd;
                        case "<": return ld < rd;
                        case "<=": return ld <= rd;
                    }
                } catch (NumberFormatException ignore) {
                    int c = lv.compareTo(rv);
                    switch (op) {
                        case ">": return c > 0;
                        case ">=": return c >= 0;
                        case "<": return c < 0;
                        case "<=": return c <= 0;
                    }
                }
            }
        }
        return false;
    }

    /** 计算一个操作数的值：若是字符串字面量(双引号)则去掉引号；若是 ${var}/裸变量则取流程变量；否则返回原文 */
    private static String valueOf(String token, Map<String, String> vars) {
        if (token == null) return null;
        token = token.trim();
        if (token.isEmpty()) return "";
        if (token.startsWith("\"") && token.endsWith("\"") && token.length() >= 2) {
            return token.substring(1, token.length() - 1);
        }
        // 去除可能残留的 ${}
        if (token.startsWith("${") && token.endsWith("}")) {
            token = token.substring(2, token.length() - 1);
        }
        if (vars != null && vars.containsKey(token)) {
            return vars.get(token);
        }
        // 函数或字面量原样返回
        return token;
    }

    private static String[] splitTopLevel(String s, String sep) {
        java.util.List<String> list = new java.util.ArrayList<>();
        int depth = 0;
        int start = 0;
        boolean inStr = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') inStr = !inStr;
            if (!inStr) {
                if (c == '(') depth++;
                else if (c == ')') depth--;
                else if (depth == 0 && i + sep.length() <= s.length() && s.substring(i, i + sep.length()).equals(sep)) {
                    list.add(s.substring(start, i));
                    start = i + sep.length();
                    i += sep.length() - 1;
                }
            }
        }
        list.add(s.substring(start));
        return list.toArray(new String[0]);
    }

    private static int indexOfTopLevel(String s, String sep) {
        int depth = 0;
        boolean inStr = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') inStr = !inStr;
            if (!inStr) {
                if (c == '(') depth++;
                else if (c == ')') depth--;
                else if (depth == 0 && i + sep.length() <= s.length() && s.substring(i, i + sep.length()).equals(sep)) {
                    // avoid matching '>' inside '>=' etc handled by order
                    return i;
                }
            }
        }
        return -1;
    }

    private static int indexOfKeyword(String s, String kw) {
        Pattern p = Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(kw) + "(?![A-Za-z0-9_])");
        Matcher m = p.matcher(s);
        return m.find() ? m.start() : -1;
    }

    private static boolean balanced(String s) {
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth < 0) return false;
            }
        }
        return depth == 0;
    }
}
