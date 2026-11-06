package com.och.system.service;

import com.github.pagehelper.PageInfo;
import com.och.common.base.IBaseService;
import com.och.system.domain.entity.Customer;
import com.och.system.domain.query.customer.CustomerAddQuery;
import com.och.system.domain.query.customer.CustomerQuery;
import com.och.system.domain.vo.customer.CustomerVo;

import java.util.List;

/**
 * 基础客户服务接口（任务 6.2）。
 *
 * @author och
 */
public interface ICustomerService extends IBaseService<Customer> {

    /**
     * 新增客户（号码规范化 + 重复检查）。
     */
    void add(CustomerAddQuery query);

    /**
     * 修改客户（号码规范化 + 重复检查，排除自身）。
     */
    void update(CustomerAddQuery query);

    /**
     * 软删除（置 del_flag）。
     */
    void delete(CustomerQuery query);

    /**
     * 启用/停用。
     */
    Boolean updateStatus(Long id, Integer status);

    /**
     * 详情。
     */
    CustomerVo getDetail(Long id);

    /**
     * 分页列表。
     */
    PageInfo<CustomerVo> getPageList(CustomerQuery query);

    /**
     * 按电话号码查询客户（来电弹屏用）。号码会自动规范化；
     * 同时匹配主电话与备用电话，未命中返回 null。
     */
    CustomerVo getByPhone(String phone);

    /**
     * 校验电话号码是否已被其他客户占用（重复检查），供外部模块调用。
     *
     * @param normalizedPhone 已规范化的号码
     * @param excludeId       排除的客户ID，新增时传 null
     */
    boolean isPhoneDuplicated(String normalizedPhone, Long excludeId);

    /**
     * 批量按号码查询，供来电弹屏批量场景使用。
     */
    List<CustomerVo> getListByPhones(List<String> phones);
}
