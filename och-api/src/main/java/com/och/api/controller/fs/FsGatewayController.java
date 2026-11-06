package com.och.api.controller.fs;

import com.github.pagehelper.PageInfo;
import com.och.common.annotation.Log;
import com.och.common.base.BaseController;
import com.och.common.base.ResResult;
import com.och.common.enums.BusinessTypeEnum;
import com.och.esl.service.IFsGatewayStatusService;
import com.och.system.domain.entity.FsSipGateway;
import com.och.system.domain.query.fssip.FsSipGatewayAddQuery;
import com.och.system.domain.query.fssip.FsSipGatewayQuery;
import com.och.system.domain.vo.fssip.GatewayStatusVo;
import com.och.system.service.IFsSipGatewayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author danmo
 * @date 2023年09月11日 13:37
 */
@Tag(name = "fs网关管理")
@Slf4j
@RestController
@RequestMapping("/system/v1/gateway")
public class FsGatewayController extends BaseController {

    @Autowired
    private IFsSipGatewayService iFsSipGatewayService;

    @Autowired
    private IFsGatewayStatusService iFsGatewayStatusService;

    @Log(title = "新增网关", businessType = BusinessTypeEnum.INSERT)
    @PreAuthorize("@authz.hasPerm('system:fs:gateway:add')")
    @Operation(summary = "新增网关", method = "POST")
    @PostMapping("/add")
    public ResResult add(@RequestBody @Validated FsSipGatewayAddQuery query) {
        iFsSipGatewayService.add(query);
        reloadSafely("add");
        return success();
    }

    @Log(title = "修改网关", businessType = BusinessTypeEnum.UPDATE)
    @PreAuthorize("@authz.hasPerm('system:fs:gateway:edit')")
    @Operation(summary = "修改网关", method = "POST")
    @PostMapping("/edit/{id}")
    public ResResult edit(@PathVariable("id") Long id, @RequestBody @Validated FsSipGatewayAddQuery query) {
        query.setId(id);
        iFsSipGatewayService.edit(query);
        reloadSafely("edit");
        return success();
    }

    @Log(title = "网关详情", businessType = BusinessTypeEnum.SELECT)
    @PreAuthorize("@authz.hasPerm('system:fs:gateway:get')")
    @Operation(summary = "网关详情", method = "POST")
    @PostMapping("/get/{id}")
    public ResResult<FsSipGateway> get(@PathVariable("id") Long id) {
        return success(iFsSipGatewayService.getDetail(id));
    }

    @Log(title = "删除网关", businessType = BusinessTypeEnum.DELETE)
    @PreAuthorize("@authz.hasPerm('system:fs:gateway:delete')")
    @Operation(summary = "删除网关", method = "POST")
    @PostMapping("/delete")
    public ResResult delete(@RequestBody FsSipGatewayQuery query) {
        // 删除前先 killgw，避免 FreeSWITCH 残留同名网关
        killGatewaySafely(query.getId());
        iFsSipGatewayService.delete(query);
        reloadSafely("delete");
        return success();
    }

    @Log(title = "网关列表(分页)", businessType = BusinessTypeEnum.SELECT)
    @PreAuthorize("@authz.hasPerm('system:fs:gateway:page:list')")
    @Operation(summary = "网关列表(分页)", method = "POST")
    @PostMapping("/page/list")
    public ResResult<PageInfo<FsSipGateway>> pageList(@RequestBody FsSipGatewayQuery query) {
        List<FsSipGateway> list = iFsSipGatewayService.getPageList(query);
        PageInfo<FsSipGateway> pageInfo = new PageInfo<>(list);
        return success(pageInfo);
    }

    @Log(title = "网关列表(不分页)", businessType = BusinessTypeEnum.SELECT)
    @PreAuthorize("@authz.hasPerm('system:fs:gateway:list')")
    @Operation(summary = "网关列表(不分页)", method = "POST")
    @PostMapping("/list")
    public ResResult<List<FsSipGateway>> list(@RequestBody FsSipGatewayQuery query) {
        List<FsSipGateway> list = iFsSipGatewayService.getList(query);
        return success(list);
    }

    @Log(title = "网关状态查询", businessType = BusinessTypeEnum.SELECT)
    @PreAuthorize("@authz.hasPerm('system:fs:gateway:list')")
    @Operation(summary = "查询单个网关实时注册状态(sofia status gateway)", method = "POST")
    @PostMapping("/status/{id}")
    public ResResult<GatewayStatusVo> status(@PathVariable("id") Long id) {
        return success(iFsGatewayStatusService.getStatus(id));
    }

    @Log(title = "网关状态列表", businessType = BusinessTypeEnum.SELECT)
    @PreAuthorize("@authz.hasPerm('system:fs:gateway:list')")
    @Operation(summary = "查询全部网关实时状态(sofia status)", method = "POST")
    @PostMapping("/status/list")
    public ResResult<List<GatewayStatusVo>> statusList() {
        return success(iFsGatewayStatusService.listStatus());
    }

    @Log(title = "网关动态重载", businessType = BusinessTypeEnum.UPDATE)
    @PreAuthorize("@authz.hasPerm('system:fs:gateway:edit')")
    @Operation(summary = "重新扫描 external profile 加载动态网关(sofia profile external rescan)", method = "POST")
    @PostMapping("/reload")
    public ResResult<String> reload() {
        return success(iFsGatewayStatusService.reloadExternal());
    }

    /**
     * 网关变更后安全重载：无可用 ESL 连接(如未连真实 FreeSWITCH)时仅记录日志，不抛异常。
     */
    private void reloadSafely(String action) {
        try {
            String result = iFsGatewayStatusService.reloadExternal();
            log.info("网关变更后重载 external action:{} result:{}", action, result);
        } catch (Exception e) {
            log.warn("网关变更后重载失败 action:{} msg:{}", action, e.getMessage());
        }
    }

    private void killGatewaySafely(Long id) {
        if (id == null) {
            return;
        }
        try {
            FsSipGateway gateway = iFsSipGatewayService.getDetail(id);
            if (gateway != null && gateway.getName() != null) {
                iFsGatewayStatusService.killGateway(gateway.getName());
            }
        } catch (Exception e) {
            log.warn("删除网关前 killgw 失败 id:{} msg:{}", id, e.getMessage());
        }
    }

}
