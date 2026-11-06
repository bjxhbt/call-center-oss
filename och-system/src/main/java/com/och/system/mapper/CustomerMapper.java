package com.och.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.och.system.domain.entity.Customer;
import com.och.system.domain.query.customer.CustomerQuery;
import com.och.system.domain.vo.customer.CustomerVo;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 基础客户 Mapper（任务 6.2）。
 *
 * @author och
 */
@Mapper
public interface CustomerMapper extends BaseMapper<Customer> {

    /**
     * 分页/条件查询客户列表。
     */
    List<CustomerVo> getPageList(CustomerQuery query);

    /**
     * 按主键查询客户详情。
     */
    CustomerVo getDetail(Long id);
}
