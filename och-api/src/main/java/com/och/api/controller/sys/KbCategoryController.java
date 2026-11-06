package com.och.api.controller.sys;

import com.och.common.annotation.Log;
import com.och.common.base.BaseController;
import com.och.common.base.ResResult;
import com.och.common.enums.BusinessTypeEnum;
import com.och.system.domain.entity.KbCategory;
import com.och.system.domain.query.kb.KbCategoryAddQuery;
import com.och.system.domain.query.kb.KbCategoryQuery;
import com.och.system.service.IKbCategoryService;
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

@Tag(name = "知识库分类")
@RestController
@RequestMapping("/v1/knowledge/category")
public class KbCategoryController extends BaseController {

    @Autowired
    private IKbCategoryService iKbCategoryService;

    @Log(title = "新增知识库分类", businessType = BusinessTypeEnum.INSERT)
    @PostMapping("/add")
    @Operation(summary = "新增知识库分类", method = "POST")
    public ResResult add(@Validated @RequestBody KbCategoryAddQuery query) {
        iKbCategoryService.add(query);
        return success();
    }

    @Log(title = "修改知识库分类", businessType = BusinessTypeEnum.UPDATE)
    @PostMapping("/edit/{id}")
    @Operation(summary = "修改知识库分类", method = "POST")
    public ResResult edit(@PathVariable("id") Long id, @Validated @RequestBody KbCategoryAddQuery query) {
        query.setId(id);
        iKbCategoryService.edit(query);
        return success();
    }

    @Log(title = "删除知识库分类", businessType = BusinessTypeEnum.DELETE)
    @PostMapping("/delete")
    @Operation(summary = "删除知识库分类", method = "POST")
    public ResResult delete(@RequestBody KbCategoryQuery query) {
        iKbCategoryService.delete(query);
        return success();
    }

    @Log(title = "知识库分类树", businessType = BusinessTypeEnum.SELECT)
    @PostMapping("/tree")
    @Operation(summary = "知识库分类树", method = "POST")
    public ResResult tree(@RequestBody KbCategoryQuery query) {
        return success(iKbCategoryService.treeList(query));
    }

    @Log(title = "知识库分类列表", businessType = BusinessTypeEnum.SELECT)
    @PostMapping("/list")
    @Operation(summary = "知识库分类列表", method = "POST")
    public ResResult<List<KbCategory>> list(@RequestBody KbCategoryQuery query) {
        return success(iKbCategoryService.list(query));
    }
}
