package com.och.system.util;

import com.och.system.domain.vo.fssip.GatewayStatusVo;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * FreeSWITCH "sofia status gateway &lt;name&gt;" / "sofia status" 文本解析器(纯逻辑，便于单测)。
 *
 * @author och
 */
public final class SofiaStatusParser {

    /** 网关状态码 */
    public static final int STATE_UNKNOWN = 0;
    public static final int STATE_REGISTERED = 1;
    public static final int STATE_TRYING = 2;
    public static final int STATE_FAILED = 3;
    public static final int STATE_NOT_FOUND = 4;

    private SofiaStatusParser() {
    }

    /**
     * 解析 "sofia status gateway &lt;name&gt;" 输出为结构化状态。
     */
    public static GatewayStatusVo parseGateway(String output, String gatewayName) {
        GatewayStatusVo vo = new GatewayStatusVo();
        vo.setName(gatewayName);
        vo.setRaw(output);
        if (isError(output)) {
            vo.setStateCode(STATE_NOT_FOUND);
            vo.setState("NOT_FOUND");
            vo.setFailReason(extractError(output));
            return vo;
        }
        Map<String, String> kv = parseKeyValue(output);
        vo.setName(firstNonBlank(kv.get("Name"), gatewayName));
        vo.setProfile(kv.get("Profile"));
        vo.setProxy(kv.get("Proxy"));
        vo.setRealm(kv.get("Realm"));
        vo.setUsername(kv.get("Username"));
        vo.setRegister(parseRegister(kv.get("Register")));
        String state = StringUtils.defaultString(kv.get("State"));
        vo.setState(state);
        vo.setStateCode(mapState(state));
        vo.setFailReason(buildFailReason(state, vo.getStateCode(), kv.get("Fail-Reason"), kv.get("Ping-Status")));
        return vo;
    }

    /**
     * 解析 "sofia status" 表格输出，按网关名称提取状态行(仅 Type=gateway 的行)。
     * 返回 map: gatewayName -&gt; 原始行(用于回显)，调用方再按需 parseGateway。
     */
    public static Map<String, String> parseGatewayList(String output) {
        Map<String, String> result = new LinkedHashMap<>();
        if (StringUtils.isBlank(output)) {
            return result;
        }
        for (String line : output.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (StringUtils.isEmpty(trimmed) || trimmed.startsWith("===")) {
                continue;
            }
            // 表格行: Name Type Data State，以空白分隔
            String[] parts = trimmed.split("\\s+");
            if (parts.length >= 4 && StringUtils.equalsIgnoreCase(parts[1], "gateway")) {
                result.put(parts[0], trimmed);
            }
        }
        return result;
    }

    public static int mapState(String state) {
        if (StringUtils.isBlank(state)) {
            return STATE_UNKNOWN;
        }
        String s = state.toUpperCase();
        if (s.contains("REGED") || s.contains("REGISTERED")) {
            return STATE_REGISTERED;
        }
        if (s.contains("TRYING") || s.contains("REGISTERING") || s.contains("REG_TRYING")) {
            return STATE_TRYING;
        }
        if (s.contains("NOREG") || s.contains("FAIL") || s.contains("FAILED") || s.contains("TIMEOUT")) {
            return STATE_FAILED;
        }
        return STATE_UNKNOWN;
    }

    public static String stateDesc(int code) {
        return switch (code) {
            case STATE_REGISTERED -> "已注册(REGED)";
            case STATE_TRYING -> "注册中(TRYING)";
            case STATE_FAILED -> "注册失败(NOREG/FAILED)";
            case STATE_NOT_FOUND -> "网关不存在";
            default -> "未知";
        };
    }

    private static Map<String, String> parseKeyValue(String output) {
        Map<String, String> map = new LinkedHashMap<>();
        if (StringUtils.isBlank(output)) {
            return map;
        }
        for (String line : output.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (StringUtils.isEmpty(trimmed)) {
                continue;
            }
            String[] parts = trimmed.split("\\s+", 2);
            if (parts.length == 2 && isKey(parts[0])) {
                map.put(parts[0], parts[1].trim());
            }
        }
        return map;
    }

    private static boolean isKey(String token) {
        if (StringUtils.isBlank(token) || !Character.isLetter(token.charAt(0))) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '-' || c == '_')) {
                return false;
            }
        }
        return true;
    }

    private static boolean isError(String output) {
        if (StringUtils.isBlank(output)) {
            return true;
        }
        String lower = output.toLowerCase();
        return lower.startsWith("-err")
                || lower.contains("no such gateway")
                || lower.contains("gateway not found")
                || lower.contains("invalid");
    }

    private static String extractError(String output) {
        if (StringUtils.isBlank(output)) {
            return "无返回(FreeSWITCH 未连接或网关不存在)";
        }
        for (String line : output.split("\\r?\\n")) {
            String t = line.trim();
            if (t.startsWith("-ERR") || t.toLowerCase().contains("no such gateway") || t.toLowerCase().contains("not found")) {
                return t;
            }
        }
        return "解析失败";
    }

    private static Integer parseRegister(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        String s = value.trim().toLowerCase();
        if (s.startsWith("true") || s.equals("t") || s.equals("1")) {
            return 1;
        }
        if (s.startsWith("false") || s.equals("f") || s.equals("0")) {
            return 0;
        }
        return null;
    }

    private static String buildFailReason(String state, int code, String failReason, String pingStatus) {
        if (code == STATE_REGISTERED) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("state=").append(StringUtils.defaultString(state));
        if (StringUtils.isNotBlank(failReason)) {
            sb.append(", failReason=").append(failReason);
        }
        if (StringUtils.isNotBlank(pingStatus)) {
            sb.append(", pingStatus=").append(pingStatus);
        }
        sb.append(" (").append(stateDesc(code)).append(")");
        return sb.toString();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (StringUtils.isNotBlank(v)) {
                return v;
            }
        }
        return null;
    }
}
