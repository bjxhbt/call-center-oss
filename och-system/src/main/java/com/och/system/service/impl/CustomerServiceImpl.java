package com.och.system.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.pagehelper.PageInfo;
import com.och.common.base.BaseServiceImpl;
import com.och.common.enums.DeleteStatusEnum;
import com.och.system.constant.ErrorCode;
import com.och.system.domain.entity.Customer;
import com.och.system.domain.query.customer.CustomerAddQuery;
import com.och.system.domain.query.customer.CustomerQuery;
import com.och.system.domain.vo.customer.CustomerVo;
import com.och.system.exception.BusinessException;
import com.och.system.mapper.CustomerMapper;
import com.och.system.service.ICustomerService;
import com.och.system.util.PhoneUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 基础客户服务实现（任务 6.2）。
 *
 * @author och
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerServiceImpl extends BaseServiceImpl<CustomerMapper, Customer> implements ICustomerService {

    @Override
    public void add(CustomerAddQuery query) {
        String phone = requireAndNormalize(query.getPhone());
        String backupPhone = PhoneUtil.normalize(query.getBackupPhone());
        // 主号与备用号不能相同
        if (backupPhone != null && Objects.equals(phone, backupPhone)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "backupPhone", "备用电话不能与主电话相同");
        }
        if (isPhoneDuplicated(phone, null)) {
            throw new BusinessException(ErrorCode.CUSTOMER_PHONE_REPEAT, "phone");
        }
        Customer customer = new Customer();
        BeanUtils.copyProperties(query, customer);
        customer.setPhone(phone);
        customer.setBackupPhone(backupPhone);
        if (customer.getStatus() == null) {
            customer.setStatus(1);
        }
        if (customer.getSource() == null) {
            customer.setSource(0);
        }
        save(customer);
    }

    @Override
    public void update(CustomerAddQuery query) {
        Customer existing = getById(query.getId());
        if (Objects.isNull(existing)) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND);
        }
        String phone = requireAndNormalize(query.getPhone());
        String backupPhone = PhoneUtil.normalize(query.getBackupPhone());
        if (backupPhone != null && Objects.equals(phone, backupPhone)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "backupPhone", "备用电话不能与主电话相同");
        }
        if (isPhoneDuplicated(phone, query.getId())) {
            throw new BusinessException(ErrorCode.CUSTOMER_PHONE_REPEAT, "phone");
        }
        BeanUtils.copyProperties(query, existing);
        existing.setPhone(phone);
        existing.setBackupPhone(backupPhone);
        updateById(existing);
    }

    @Override
    public void delete(CustomerQuery query) {
        List<Long> ids = new LinkedList<>();
        if (Objects.nonNull(query.getId())) {
            ids.add(query.getId());
        }
        if (CollectionUtil.isNotEmpty(query.getIds())) {
            ids.addAll(query.getIds());
        }
        if (CollectionUtil.isEmpty(ids)) {
            return;
        }
        List<Customer> list = ids.stream().map(id -> {
            Customer c = new Customer();
            c.setId(id);
            c.setDelFlag(DeleteStatusEnum.DELETE_YES.getIndex());
            return c;
        }).collect(Collectors.toList());
        updateBatchById(list);
    }

    @Override
    public Boolean updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "status", "状态取值非法(0-停用 1-正常)");
        }
        Customer customer = new Customer();
        customer.setId(id);
        customer.setStatus(status);
        return updateById(customer);
    }

    @Override
    public CustomerVo getDetail(Long id) {
        return baseMapper.getDetail(id);
    }

    @Override
    public PageInfo<CustomerVo> getPageList(CustomerQuery query) {
        // 查询入参中的号码统一规范化，保证与入库口径一致
        if (query.getPhone() != null) {
            query.setPhone(PhoneUtil.normalize(query.getPhone()));
        }
        startPage(query.getPageIndex(), query.getPageSize(), query.getSortField(), query.getSort());
        List<CustomerVo> list = baseMapper.getPageList(query);
        return new PageInfo<>(list);
    }

    @Override
    public CustomerVo getByPhone(String phone) {
        String normalized = PhoneUtil.normalize(phone);
        if (normalized == null) {
            return null;
        }
        CustomerQuery query = new CustomerQuery();
        query.setPhone(normalized);
        query.setPageIndex(1);
        query.setPageSize(1);
        List<CustomerVo> list = baseMapper.getPageList(query);
        return CollectionUtil.isEmpty(list) ? null : list.get(0);
    }

    @Override
    public boolean isPhoneDuplicated(String normalizedPhone, Long excludeId) {
        if (normalizedPhone == null || normalizedPhone.isEmpty()) {
            return false;
        }
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<Customer>()
                .eq(Customer::getDelFlag, DeleteStatusEnum.DELETE_NO.getIndex())
                .and(w -> w.eq(Customer::getPhone, normalizedPhone)
                        .or().eq(Customer::getBackupPhone, normalizedPhone));
        if (excludeId != null) {
            wrapper.ne(Customer::getId, excludeId);
        }
        return count(wrapper) > 0;
    }

    @Override
    public List<CustomerVo> getListByPhones(List<String> phones) {
        if (CollectionUtil.isEmpty(phones)) {
            return Collections.emptyList();
        }
        List<String> normalized = phones.stream()
                .map(PhoneUtil::normalize)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<Customer>()
                .eq(Customer::getDelFlag, DeleteStatusEnum.DELETE_NO.getIndex())
                .and(w -> w.in(Customer::getPhone, normalized)
                        .or().in(Customer::getBackupPhone, normalized));
        List<Customer> list = list(wrapper);
        return list.stream().map(c -> {
            CustomerVo vo = new CustomerVo();
            BeanUtils.copyProperties(c, vo);
            return vo;
        }).collect(Collectors.toList());
    }

    private String requireAndNormalize(String phone) {
        String normalized = PhoneUtil.normalize(phone);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_PHONE_EMPTY, "phone");
        }
        return normalized;
    }
}
