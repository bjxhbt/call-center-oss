package com.och.api.controller.call;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.och.common.base.BaseController;
import com.och.common.base.ResResult;
import com.och.system.domain.entity.WorkOrder;
import com.och.system.service.IWorkOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 工单管理接口
 */
@Slf4j
@Tag(name = "工单管理")
@RequestMapping("/v1/workorder")
@RestController
public class WorkOrderController extends BaseController {

    @Autowired
    private IWorkOrderService workOrderService;

    @Operation(description = "创建工单")
    @PostMapping("/create")
    public ResResult<Long> create(@RequestBody WorkOrder params) {
        try {
            if (params.getWorkOrderType() == null || params.getWorkOrderType().isBlank()) {
                return ResResult.error("工单类型不能为空");
            }
            if (params.getAcceptanceContent() == null || params.getAcceptanceContent().isBlank()) {
                return ResResult.error("受理内容不能为空");
            }
            return success(workOrderService.saveDetails(params));
        } catch (Exception e) {
            log.error("创建工单失败: {}", e.getMessage(), e);
            return ResResult.error("创建工单失败: " + e.getMessage());
        }
    }

    @Operation(description = "按客户号码查询历史工单")
    @GetMapping("/history")
    public ResResult<List<WorkOrder>> history(@RequestParam String phone,
                                               @RequestParam(required = false) Long excludeId) {
        return success(workOrderService.listHistory(phone, excludeId));
    }

    @Operation(description = "工单列表(分页)")
    @GetMapping("/list")
    public ResResult<Page<WorkOrder>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer priority,
            @RequestParam(required = false) String keyword) {
        try {
            Page<WorkOrder> pageParam = new Page<>(page, size);
            QueryWrapper<WorkOrder> wrapper = new QueryWrapper<>();
            if (status != null) {
                wrapper.eq("status", status);
            }
            if (priority != null) {
                wrapper.eq("priority", priority);
            }
            if (keyword != null && !keyword.isEmpty()) {
                wrapper.and(w -> w.like("title", keyword)
                        .or().like("description", keyword)
                        .or().like("customer_name", keyword)
                        .or().like("caller_number", keyword)
                        .or().like("acceptance_content", keyword));
            }
            wrapper.orderByDesc("create_time");

            Page<WorkOrder> result = workOrderService.page(pageParam, wrapper);
            return success(result);
        } catch (Exception e) {
            log.error("获取工单列表失败: {}", e.getMessage(), e);
            return ResResult.error("获取工单列表失败: " + e.getMessage());
        }
    }

    @Operation(description = "工单详情")
    @GetMapping("/get/{id}")
    public ResResult<WorkOrder> getDetail(@PathVariable Long id) {
        WorkOrder order = workOrderService.getById(id);
        if (order == null) {
            return ResResult.error("工单不存在");
        }
        return success(order);
    }

    @Operation(description = "更新工单状态")
    @PostMapping("/update/{id}")
    public ResResult<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        try {
            Integer status = params.get("status") != null ?
                    Integer.valueOf(params.get("status").toString()) : null;
            String remark = (String) params.get("remark");
            Long handlerId = params.get("handlerId") != null ?
                    Long.valueOf(params.get("handlerId").toString()) : null;
            String handlerName = (String) params.get("handlerName");

            if (status == null) {
                return ResResult.error("状态不能为空");
            }
            workOrderService.updateStatus(id, status, remark, handlerId, handlerName);
            return success();
        } catch (Exception e) {
            log.error("更新工单状态失败: {}", e.getMessage(), e);
            return ResResult.error("更新工单状态失败: " + e.getMessage());
        }
    }

    @Operation(description = "删除工单")
    @PostMapping("/delete/{id}")
    public ResResult<Void> delete(@PathVariable Long id) {
        try {
            workOrderService.removeById(id);
            return success();
        } catch (Exception e) {
            log.error("删除工单失败: {}", e.getMessage(), e);
            return ResResult.error("删除工单失败: " + e.getMessage());
        }
    }
}
