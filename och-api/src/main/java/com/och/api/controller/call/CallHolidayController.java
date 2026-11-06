package com.och.api.controller.call;

import com.github.pagehelper.PageInfo;
import com.och.common.annotation.Log;
import com.och.common.base.BaseController;
import com.och.common.base.ResResult;
import com.och.common.enums.BusinessTypeEnum;
import com.och.system.domain.entity.CallHoliday;
import com.och.system.domain.query.holiday.CallHolidayAddQuery;
import com.och.system.domain.query.holiday.CallHolidayQuery;
import com.och.system.service.ICallHolidayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Tag(name = "假期日历管理")
@RestController
@RequestMapping("/call/v1/holiday")
public class CallHolidayController extends BaseController {
    @Autowired
    private ICallHolidayService iCallHolidayService;

    @Log(title = "新增假期", businessType = BusinessTypeEnum.INSERT)
    @Operation(summary = "新增假期", method = "POST")
    @PostMapping("/add")
    public ResResult add(@RequestBody @Validated CallHolidayAddQuery query) {
        iCallHolidayService.add(query);
        return success();
    }

    @Log(title = "修改假期", businessType = BusinessTypeEnum.UPDATE)
    @Operation(summary = "修改假期", method = "POST")
    @PostMapping("/edit/{id}")
    public ResResult edit(@PathVariable("id") Long id, @RequestBody @Validated CallHolidayAddQuery query) {
        query.setId(id);
        iCallHolidayService.update(query);
        return success();
    }

    @Log(title = "删除假期", businessType = BusinessTypeEnum.DELETE)
    @Operation(summary = "删除假期", method = "POST")
    @PostMapping("/delete")
    public ResResult delete(@RequestBody CallHolidayQuery query) {
        iCallHolidayService.delete(query);
        return success();
    }

    @Operation(summary = "假期详情", method = "POST")
    @PostMapping("/get/{id}")
    public ResResult<CallHoliday> getDetail(@PathVariable("id") Long id) {
        return success(iCallHolidayService.getById(id));
    }

    @Operation(summary = "假期列表(分页)", method = "POST")
    @PostMapping("/page/getList")
    public ResResult<PageInfo<CallHoliday>> getPageList(@RequestBody CallHolidayQuery query) {
        return success(new PageInfo<>(iCallHolidayService.getPageList(query)));
    }

    @Operation(summary = "假期列表", method = "POST")
    @PostMapping("/getList")
    public ResResult<List<CallHoliday>> getList(@RequestBody CallHolidayQuery query) {
        return success(iCallHolidayService.getList(query));
    }
}