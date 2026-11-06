package com.och.system.constant;

/**
 * 统一表单校验规则（任务 10.1）。
 *
 * <p>前后端共用同一套正则与边界：后端在 Query/VO 上用 {@code jakarta.validation} 约束注解引用，
 * 前端在 {@code src/consts/validate.ts} 镜像相同正则，避免出现“前端放过、后端拒绝”或反之的口径不一致。</p>
 *
 * <p>号码格式说明：支持国内手机号 1[3-9]xxxxxxxxx 与带区号的座机
 * (如 010-12345678 / 0755-1234567)。呼叫中心外显号码、SIP 账号多为纯数字，校验时按
 * {@link #NUMBER_PATTERN}（纯数字，可含 *#）放宽；客户联系电话按 {@link #PHONE_PATTERN} 严格校验。</p>
 *
 * @author och
 */
public final class ValidationConstants {

    private ValidationConstants() {
    }

    /** IPv4 地址。 */
    public static final String IP_PATTERN =
            "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$";

    /** 端口 1-65535。 */
    public static final String PORT_PATTERN =
            "^([1-9]\\d{0,3}|[1-5]\\d{4}|6[0-4]\\d{3}|65[0-4]\\d{2}|655[0-2]\\d|6553[0-5])$";

    /** SIP 账号 / 外显号码 / 前缀：纯数字，长度 1-32，可含 * #（用于分机号等）。 */
    public static final String NUMBER_PATTERN = "^[0-9*#]{1,32}$";

    /** 客户联系电话：国内手机号或带区号座机。 */
    public static final String PHONE_PATTERN =
            "^(1[3-9]\\d{9})|(0\\d{2,3}-?\\d{7,8})$";

    /** 号码前缀（出局加拨前缀，如 9/0）：1-8 位数字。 */
    public static final String PREFIX_PATTERN = "^[0-9]{1,8}$";

    /** 域名/Realm（SIP realm、服务器地址）：允许域名或 IP，可带端口。 */
    public static final String HOST_PATTERN =
            "^(([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}"
            + "|(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)(\\.(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)){3})"
            + "(:[1-9]\\d{0,4})?$";

    /** 通用名称：中英文/数字/下划线/中划线，长度 1-64。 */
    public static final String NAME_PATTERN = "^[\\u4e00-\\u9fa5A-Za-z0-9_\\-]{1,64}$";

    /** 超时时间（秒）取值范围 1-86400。 */
    public static final int TIMEOUT_MIN_SECONDS = 1;
    public static final int TIMEOUT_MAX_SECONDS = 86400;

    /** 通用文本最大长度（备注等）。 */
    public static final int REMARK_MAX_LENGTH = 500;

    /** 名称最大长度。 */
    public static final int NAME_MAX_LENGTH = 64;

    /** 手机号长度（去格式化后）。 */
    public static final int MOBILE_LENGTH = 11;

    public static final String IP_MSG = "IP地址格式不正确";
    public static final String PORT_MSG = "端口必须为1-65535的整数";
    public static final String NUMBER_MSG = "号码只能包含数字(可含*#)，长度1-32";
    public static final String PHONE_MSG = "电话号码格式不正确(支持手机号或带区号座机)";
    public static final String PREFIX_MSG = "前缀只能为1-8位数字";
    public static final String HOST_MSG = "服务器地址格式不正确";
    public static final String NAME_MSG = "名称仅支持中英文/数字/下划线/中划线，长度1-64";
    public static final String TIMEOUT_MSG = "超时时间必须为1-86400秒";
    public static final String REMARK_MSG = "备注长度不能超过500";
}
