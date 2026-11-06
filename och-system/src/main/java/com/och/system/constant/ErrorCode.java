package com.och.system.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一业务错误码与中文提示（任务 10.2）。
 *
 * <p>供后端通过 {@code BusinessException} 抛出、前端 {@code src/consts/errorCode.ts} 镜像同一套码值，
 * 用于在请求层直接展示中文提示。错误码采用 5 位数字分组：</p>
 * <ul>
 *   <li>10xxx：参数 / 校验异常</li>
 *   <li>11xxx：坐席 / 用户业务异常</li>
 *   <li>12xxx：客户业务异常</li>
 *   <li>13xxx：网关 / 路由业务异常</li>
 *   <li>20xxx：SIP 异常</li>
 *   <li>21xxx：网络 / ESL 异常</li>
 *   <li>22xxx：AI(ASR) 服务异常</li>
 *   <li>23xxx：AI(TTS) 服务异常</li>
 * </ul>
 *
 * <p>通用 HTTP 状态码复用 {@code com.och.common.constant.HttpStatus}（如 400/401/403/404/409/500），
 * 本枚举只定义业务域专属码，避免与 HttpStatus 数值冲突。</p>
 *
 * @author och
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    /* ============ 10xxx 参数 / 校验异常 ============ */
    PARAM_MISSING(10001, "必填参数不能为空"),
    PARAM_INVALID(10002, "参数格式不正确"),
    PARAM_IP_INVALID(10003, "IP地址格式不正确"),
    PARAM_PORT_INVALID(10004, "端口必须为1-65535的整数"),
    PARAM_PHONE_INVALID(10005, "电话号码格式不正确"),
    PARAM_NUMBER_INVALID(10006, "号码只能包含数字，长度1-32"),
    PARAM_TIMEOUT_INVALID(10007, "超时时间必须为1-86400秒"),
    PARAM_LENGTH_INVALID(10008, "内容长度超出限制"),

    /* ============ 11xxx 坐席 / 用户业务异常 ============ */
    AGENT_NOT_FOUND(11001, "坐席不存在"),
    AGENT_SIP_BIND_REPEAT(11002, "该SIP账号已被其他有效坐席绑定"),
    AGENT_USER_NOT_BIND(11003, "当前用户未绑定坐席"),
    AGENT_STATUS_INVALID(11004, "坐席状态取值非法"),
    AGENT_DISABLED(11005, "坐席已停用，无法发起呼叫"),
    USER_NOT_LOGIN(11006, "用户未登录"),

    /* ============ 12xxx 客户业务异常 ============ */
    CUSTOMER_NOT_FOUND(12001, "客户不存在"),
    CUSTOMER_PHONE_REPEAT(12002, "客户电话号码已存在"),
    CUSTOMER_PHONE_EMPTY(12003, "客户电话号码不能为空"),

    /* ============ 13xxx 网关 / 路由业务异常 ============ */
    GATEWAY_NOT_FOUND(13001, "网关不存在"),
    GATEWAY_UNAVAILABLE(13002, "网关不可用"),
    ROUTE_NOT_FOUND(13003, "呼叫路由不存在"),
    ROUTE_NOT_GATEWAY(13004, "路由未绑定可用网关"),

    /* ============ 20xxx SIP 异常 ============ */
    SIP_REGISTER_FAIL(20001, "SIP注册失败"),
    SIP_CALL_FAIL(20002, "SIP呼叫失败"),
    SIP_AUTH_FAIL(20003, "SIP鉴权失败"),
    SIP_NOT_ANSWER(20004, "SIP无应答"),

    /* ============ 21xxx 网络 / ESL 异常 ============ */
    NETWORK_TIMEOUT(21001, "网络请求超时"),
    NETWORK_UNAVAILABLE(21002, "网络不可用"),
    ESL_CONNECT_FAIL(21003, "FreeSWITCH ESL连接失败"),
    ESL_COMMAND_FAIL(21004, "FreeSWITCH指令执行失败"),

    /* ============ 22xxx AI(ASR) 服务异常 ============ */
    ASR_SERVICE_FAIL(22001, "ASR服务异常"),
    ASR_AUTH_FAIL(22002, "ASR服务鉴权失败"),
    ASR_TIMEOUT(22003, "ASR识别超时"),
    ASR_CONNECT_FAIL(22004, "ASR连接失败"),

    /* ============ 23xxx AI(TTS) 服务异常 ============ */
    TTS_SERVICE_FAIL(23001, "TTS服务异常"),
    TTS_AUTH_FAIL(23002, "TTS服务鉴权失败"),
    TTS_TIMEOUT(23003, "TTS合成超时"),
    TTS_FILE_SAVE_FAIL(23004, "TTS音频文件保存失败");

    private final int code;
    private final String message;

}
