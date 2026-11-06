package com.och.api.controller.call;

import com.github.pagehelper.PageInfo;
import com.och.common.annotation.Log;
import com.och.common.base.BaseController;
import com.och.common.base.ResResult;
import com.och.common.enums.BusinessTypeEnum;
import com.och.system.domain.query.number.NumberTransformRuleAddQuery;
import com.och.system.domain.query.number.NumberTransformRuleQuery;
import com.och.system.domain.vo.number.NumberTransformResult;
import com.och.system.domain.vo.number.NumberTransformRuleVo;
import com.och.system.service.INumberTransformRuleService;
import com.och.system.service.INumberTransformService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 号码转换规则管理 + 号码转换预览。
 * <p>
 * 所有号码变换规则(去前缀/加前缀/整体替换/主叫替换)由数据库配置驱动，
 * 用于呼出/呼入路由阶段的号码规范化(如 918xxxxxxxxx -&gt; 018xxxxxxxxx)。
 *
 * @author och
 */
@Tag(name = "号码转换规则管理")
@RestController
@RequestMapping("call/v1/numberTransform")
public class NumberTransformController extends BaseController {

    @Autowired
    private INumberTransformRuleService iNumberTransformRuleService;

    @Autowired
    private INumberTransformService iNumberTransformService;

    @Log(title = "新增号码转换规则", businessType = BusinessTypeEnum.INSERT)
    @PreAuthorize("@authz.hasPerm('call:numberTransform:add')")
    @Operation(summary = "新增号码转换规则", method = "POST")
    @PostMapping("/add")
    public ResResult add(@RequestBody @Validated NumberTransformRuleAddQuery query) {
        iNumberTransformRuleService.add(query);
        return success();
    }

    @Log(title = "修改号码转换规则", businessType = BusinessTypeEnum.UPDATE)
    @PreAuthorize("@authz.hasPerm('call:numberTransform:edit')")
    @Operation(summary = "修改号码转换规则", method = "POST")
    @PostMapping("/edit/{id}")
    public ResResult edit(@PathVariable("id") Long id, @RequestBody @Validated NumberTransformRuleAddQuery query) {
        query.setId(id);
        iNumberTransformRuleService.edit(query);
        return success();
    }

    @Log(title = "删除号码转换规则", businessType = BusinessTypeEnum.DELETE)
    @PreAuthorize("@authz.hasPerm('call:numberTransform:delete')")
    @Operation(summary = "删除号码转换规则", method = "POST")
    @PostMapping("/delete")
    public ResResult delete(@RequestBody NumberTransformRuleQuery query) {
        iNumberTransformRuleService.delete(query);
        return success();
    }

    @Log(title = "号码转换规则详情", businessType = BusinessTypeEnum.SELECT)
    @PreAuthorize("@authz.hasPerm('call:numberTransform:get')")
    @Operation(summary = "号码转换规则详情", method = "POST")
    @PostMapping("/get/{id}")
    public ResResult<NumberTransformRuleVo> get(@PathVariable("id") Long id) {
        return success(iNumberTransformRuleService.getDetail(id));
    }

    @Log(title = "号码转换规则列表(分页)", businessType = BusinessTypeEnum.SELECT)
    @PreAuthorize("@authz.hasPerm('call:numberTransform:page:list')")
    @Operation(summary = "号码转换规则列表(分页)", method = "POST")
    @PostMapping("/page/list")
    public ResResult<PageInfo<NumberTransformRuleVo>> pageList(@RequestBody NumberTransformRuleQuery query) {
        List<NumberTransformRuleVo> list = iNumberTransformRuleService.getPageList(query);
        PageInfo<NumberTransformRuleVo> pageInfo = PageInfo.of(list);
        return success(pageInfo);
    }

    @Log(title = "号码转换预览", businessType = BusinessTypeEnum.SELECT)
    @PreAuthorize("@authz.hasPerm('call:numberTransform:preview')")
    @Operation(summary = "号码转换预览(不入库，仅按当前规则计算结果)", method = "POST")
    @PostMapping("/preview")
    public ResResult<NumberTransformResult> preview(@RequestBody NumberTransformPreviewQuery query) {
        NumberTransformResult result = iNumberTransformService.transform(
                query.getDirection(), query.getNumber(), query.getCaller(), query.getGatewayId());
        return success(result);
    }

    @lombok.Data
    public static class NumberTransformPreviewQuery {
        @io.swagger.v3.oas.annotations.media.Schema(description = "方向 1-呼入 2-呼出(与DirectionEnum一致)")
        private Integer direction;
        @io.swagger.v3.oas.annotations.media.Schema(description = "原始号码")
        private String number;
        @io.swagger.v3.oas.annotations.media.Schema(description = "原始主叫号码")
        private String caller;
        @io.swagger.v3.oas.annotations.media.Schema(description = "目标网关ID")
        private Long gatewayId;
    }
}
