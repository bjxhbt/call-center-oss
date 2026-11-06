package com.och.api.controller.sys;

import com.github.pagehelper.PageInfo;
import com.och.common.annotation.Log;
import com.och.common.base.BaseController;
import com.och.common.base.ResResult;
import com.och.common.enums.BusinessTypeEnum;
import com.och.system.domain.entity.KbEntry;
import com.och.system.domain.query.kb.KbEntryAddQuery;
import com.och.system.domain.query.kb.KbEntryQuery;
import com.och.system.service.IKbEntryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "知识库词条")
@RestController
@RequestMapping("/v1/knowledge/entry")
public class KbEntryController extends BaseController {

    @Autowired
    private IKbEntryService iKbEntryService;

    @Log(title = "新增知识库词条", businessType = BusinessTypeEnum.INSERT)
    @PostMapping("/add")
    @Operation(summary = "新增知识库词条", method = "POST")
    public ResResult add(@Validated @RequestBody KbEntryAddQuery query) {
        iKbEntryService.add(query);
        return success();
    }

    @Log(title = "修改知识库词条", businessType = BusinessTypeEnum.UPDATE)
    @PostMapping("/edit/{id}")
    @Operation(summary = "修改知识库词条", method = "POST")
    public ResResult edit(@PathVariable("id") Long id, @Validated @RequestBody KbEntryAddQuery query) {
        query.setId(id);
        iKbEntryService.edit(query);
        return success();
    }

    @Log(title = "删除知识库词条", businessType = BusinessTypeEnum.DELETE)
    @PostMapping("/delete")
    @Operation(summary = "删除知识库词条", method = "POST")
    public ResResult delete(@RequestBody KbEntryQuery query) {
        iKbEntryService.delete(query);
        return success();
    }

    @Log(title = "知识库词条分页列表", businessType = BusinessTypeEnum.SELECT)
    @PostMapping("/page/list")
    @Operation(summary = "知识库词条分页列表", method = "POST")
    public ResResult<PageInfo<KbEntry>> pageList(@RequestBody KbEntryQuery query) {
        return success(iKbEntryService.pageList(query));
    }

    @Log(title = "知识库词条列表", businessType = BusinessTypeEnum.SELECT)
    @PostMapping("/list")
    @Operation(summary = "知识库词条列表", method = "POST")
    public ResResult<List<KbEntry>> list(@RequestBody KbEntryQuery query) {
        return success(iKbEntryService.list(query));
    }

    @Log(title = "知识库词条详情", businessType = BusinessTypeEnum.SELECT)
    @PostMapping("/get/{id}")
    @Operation(summary = "知识库词条详情", method = "POST")
    public ResResult<KbEntry> get(@PathVariable("id") Long id) {
        return success(iKbEntryService.getDetail(id));
    }
}
