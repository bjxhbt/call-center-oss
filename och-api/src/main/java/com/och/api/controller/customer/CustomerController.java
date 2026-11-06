package com.och.api.controller.customer;

import com.github.pagehelper.PageInfo;
import com.och.common.annotation.Log;
import com.och.common.base.BaseController;
import com.och.common.base.ResResult;
import com.och.common.enums.BusinessTypeEnum;
import com.och.system.domain.query.customer.CustomerAddQuery;
import com.och.system.domain.query.customer.CustomerQuery;
import com.och.system.domain.vo.customer.CustomerVo;
import com.och.system.service.ICustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 基础客户管理（任务 6.2）。
 *
 * <p>提供客户新增/编辑/查询/删除/启停与按号码查询（来电弹屏）能力。</p>
 *
 * @author och
 */
@Tag(name = "基础客户管理")
@RestController
@RequestMapping("/system/v1/customer")
public class CustomerController extends BaseController {

    @Autowired
    private ICustomerService customerService;

    @Log(title = "新增客户", businessType = BusinessTypeEnum.INSERT)
    @PreAuthorize("@authz.hasPerm('system:customer:add')")
    @Operation(summary = "新增客户", method = "POST")
    @PostMapping("/add")
    public ResResult add(@RequestBody @Validated CustomerAddQuery query) {
        customerService.add(query);
        return ResResult.success();
    }

    @Log(title = "修改客户", businessType = BusinessTypeEnum.UPDATE)
    @PreAuthorize("@authz.hasPerm('system:customer:edit')")
    @Operation(summary = "修改客户", method = "POST")
    @PostMapping("/edit/{id}")
    public ResResult edit(@PathVariable("id") Long id, @RequestBody @Validated CustomerAddQuery query) {
        query.setId(id);
        customerService.update(query);
        return ResResult.success();
    }

    @Log(title = "删除客户", businessType = BusinessTypeEnum.DELETE)
    @PreAuthorize("@authz.hasPerm('system:customer:delete')")
    @Operation(summary = "删除客户(软删除)", method = "POST")
    @PostMapping("/delete")
    public ResResult delete(@RequestBody CustomerQuery query) {
        customerService.delete(query);
        return ResResult.success();
    }

    @Log(title = "客户详情", businessType = BusinessTypeEnum.SELECT)
    @PreAuthorize("@authz.hasPerm('system:customer:get')")
    @Operation(summary = "客户详情", method = "POST")
    @PostMapping("/get/{id}")
    public ResResult<CustomerVo> getDetail(@PathVariable("id") Long id) {
        return ResResult.success(customerService.getDetail(id));
    }

    @Log(title = "客户列表", businessType = BusinessTypeEnum.SELECT)
    @PreAuthorize("@authz.hasPerm('system:customer:page:list')")
    @Operation(summary = "客户分页列表", method = "POST")
    @PostMapping("/page/list")
    public ResResult<PageInfo<CustomerVo>> getPageList(@RequestBody CustomerQuery query) {
        PageInfo<CustomerVo> page = customerService.getPageList(query);
        return success(page);
    }

    /**
     * 按电话号码查询客户（来电弹屏）。
     * <p>号码自动规范化，同时匹配主电话与备用电话，未命中返回 data=null（code 仍为 200）。</p>
     */
    @Operation(summary = "按号码查询客户(来电弹屏)", method = "GET")
    @GetMapping("/getByPhone/{phone}")
    public ResResult<CustomerVo> getByPhone(@PathVariable("phone") String phone) {
        return ResResult.success(customerService.getByPhone(phone));
    }

    @Log(title = "客户启停", businessType = BusinessTypeEnum.UPDATE)
    @PreAuthorize("@authz.hasPerm('system:customer:edit')")
    @Operation(summary = "启用/停用客户", method = "POST")
    @PostMapping("/update/status/{id}")
    public ResResult updateStatus(@PathVariable("id") Long id, @RequestBody CustomerQuery query) {
        query.setId(id);
        customerService.updateStatus(query.getId(), query.getStatus());
        return ResResult.success();
    }

    /**
     * 批量按号码查询客户（弹屏/外呼批量匹配）。
     */
    @Operation(summary = "批量按号码查询客户", method = "POST")
    @PostMapping("/listByPhones")
    public ResResult<List<CustomerVo>> listByPhones(@RequestBody List<String> phones) {
        return success(customerService.getListByPhones(phones));
    }
}
