package com.och.system.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 字段级错误信息，用于表单校验失败时向前端返回“字段名 + 中文提示”清单（任务 10.2）。
 *
 * <p>前端请求层据此在对应表单项下方展示中文提示，而不是只展示一句笼统的错误。</p>
 *
 * @author och
 */
@Schema
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FieldErrorVo implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "字段名")
    private String field;

    @Schema(description = "中文错误提示")
    private String message;
}
