package com.och.system.domain.vo.fssip;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * SIP 网关实时状态(来自 FreeSWITCH "sofia status gateway &lt;name&gt;")。
 *
 * @author och
 */
@Schema
@Data
public class GatewayStatusVo {

    @Schema(description = "网关名称")
    private String name;

    @Schema(description = "网关ID")
    private Long id;

    @Schema(description = "所属 profile(external/internal)")
    private String profile;

    @Schema(description = "代理地址")
    private String proxy;

    @Schema(description = "认证域")
    private String realm;

    @Schema(description = "账号")
    private String username;

    @Schema(description = "是否注册 0-否 1-是")
    private Integer register;

    @Schema(description = "原始状态文本(REGED/NOREG/TRYING...)")
    private String state;

    @Schema(description = "状态码 0-未知 1-已注册 2-注册中 3-失败 4-不存在")
    private Integer stateCode;

    @Schema(description = "状态描述")
    private String stateDesc;

    @Schema(description = "失败原因(注册成功为空)")
    private String failReason;

    @Schema(description = "原始返回内容(调试用)")
    private String raw;
}
