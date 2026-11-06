package com.och.system.domain.vo.agent;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "SIP配置")
public class SipConfigVo {

    @Schema(description = "坐席ID")
    private Long agentId;

    @Schema(description = "坐席名称")
    private String agentName;

    @Schema(description = "SIP用户名(分机号)")
    private String sipUser;

    @Schema(description = "坐席在线状态")
    private Integer onlineStatus;

    @Schema(description = "SIP密码")
    private String password;

    @Schema(description = "FreeSWITCH地址")
    private String sipHost;

    @Schema(description = "WebSocket端口")
    private Integer wsPort;
}
