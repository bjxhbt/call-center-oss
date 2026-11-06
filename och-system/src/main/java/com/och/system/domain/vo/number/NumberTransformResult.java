package com.och.system.domain.vo.number;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 号码转换结果
 * <p>
 * 保存原始号码、转换后号码、匹配路由规则与网关，用于话单与录音关联。
 *
 * @author och
 */
@Schema
@Data
public class NumberTransformResult {

    @Schema(description = "原始号码")
    private String originalNumber;

    @Schema(description = "转换后号码")
    private String transformedNumber;

    @Schema(description = "原始主叫号码")
    private String originalCaller;

    @Schema(description = "转换后主叫号码")
    private String transformedCaller;

    @Schema(description = "是否命中规则")
    private boolean matched;

    @Schema(description = "命中规则ID")
    private Long ruleId;

    @Schema(description = "命中规则名称")
    private String ruleName;

    @Schema(description = "网关ID")
    private Long gatewayId;

    @Schema(description = "网关名称")
    private String gatewayName;

    public static NumberTransformResult notMatched(String originalNumber, String originalCaller) {
        NumberTransformResult result = new NumberTransformResult();
        result.originalNumber = originalNumber;
        result.transformedNumber = originalNumber;
        result.originalCaller = originalCaller;
        result.transformedCaller = originalCaller;
        result.matched = false;
        return result;
    }
}
