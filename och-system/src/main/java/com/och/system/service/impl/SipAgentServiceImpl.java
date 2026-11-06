package com.och.system.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.pagehelper.PageInfo;
import com.och.common.base.BaseServiceImpl;
import com.och.common.config.redis.RedisService;
import com.och.common.constant.CacheConstants;
import com.och.common.enums.DeleteStatusEnum;
import com.och.common.enums.SipAgentStatusEnum;
import com.och.system.constant.ErrorCode;
import com.och.system.domain.entity.SipAgent;
import com.och.system.domain.query.agent.SipAgentAddQuery;
import com.och.system.domain.query.agent.SipAgentQuery;
import com.och.system.domain.vo.agent.SipAgentStatusVo;
import com.och.system.domain.vo.agent.SipAgentVo;
import com.och.system.exception.BusinessException;
import com.och.system.mapper.SipAgentMapper;
import com.och.system.service.ISipAgentService;
import lombok.AllArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 坐席管理表(SipAgent)
 *
 * @author danmo
 * @date 2023-09-26 11:08:58
 */
@AllArgsConstructor
@Service
public class SipAgentServiceImpl extends BaseServiceImpl<SipAgentMapper, SipAgent> implements ISipAgentService {

    private final RedisService redisService;

    @Override
    public void add(SipAgentAddQuery query) {
        // 同一SIP账号只能绑定一个有效坐席（status=1 且未删除）
        checkAgentNumberUnique(query.getAgentNumber(), null);
        SipAgent lfsAgent = new SipAgent();
        lfsAgent.setName(query.getName());
        lfsAgent.setStatus(query.getStatus());
        lfsAgent.setUserId(query.getUserId());
        lfsAgent.setAgentNumber(query.getAgentNumber());
        save(lfsAgent);
    }

    @Override
    public void update(SipAgentAddQuery query) {
        SipAgent sipAgent = getById(query.getId());
        if (Objects.isNull(sipAgent)) {
            throw new BusinessException(ErrorCode.AGENT_NOT_FOUND);
        }
        // SIP账号变更时校验唯一性（排除自身）
        if (!Objects.equals(sipAgent.getAgentNumber(), query.getAgentNumber())) {
            checkAgentNumberUnique(query.getAgentNumber(), query.getId());
            sipAgent.setAgentNumber(query.getAgentNumber());
        }
        if (!Objects.equals(sipAgent.getUserId(), query.getUserId())){
            sipAgent.setUserId(query.getUserId());
        }
        if (!Objects.equals(sipAgent.getName(), query.getName())){
            sipAgent.setName(query.getName());
        }
        if (!Objects.equals(sipAgent.getStatus(), query.getStatus())){
            sipAgent.setStatus(query.getStatus());
        }
        if(updateById(sipAgent)){
            redisService.delCacheMapValue(CacheConstants.AGENT_CURRENT_STATUS_KEY, sipAgent.getId().toString());
        };
    }

    /**
     * 校验同一 SIP 账号是否已被其他有效坐席（未删除且 status=1）绑定。
     *
     * @param agentNumber SIP 账号
     * @param excludeId   更新时排除的坐席主键，新增时传 null
     */
    private void checkAgentNumberUnique(String agentNumber, Long excludeId) {
        if (agentNumber == null || agentNumber.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "agentNumber", "坐席账号不能为空");
        }
        LambdaQueryWrapper<SipAgent> wrapper = new LambdaQueryWrapper<SipAgent>()
                .eq(SipAgent::getAgentNumber, agentNumber)
                .eq(SipAgent::getStatus, 1)
                .eq(SipAgent::getDelFlag, DeleteStatusEnum.DELETE_NO.getIndex());
        if (excludeId != null) {
            wrapper.ne(SipAgent::getId, excludeId);
        }
        long count = count(wrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.AGENT_SIP_BIND_REPEAT, "agentNumber");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void delete(SipAgentQuery query) {
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
        List<SipAgent> list = ids.stream().map(id -> {
            SipAgent agent = new SipAgent();
            agent.setId(id);
            agent.setDelFlag(DeleteStatusEnum.DELETE_YES.getIndex());
            return agent;
        }).collect(Collectors.toList());
        if(updateBatchById(list)){
            String[] idArray = ids.stream()
                    .map(String::valueOf)
                    .toArray(String[]::new);
            redisService.delCacheMapValue(CacheConstants.AGENT_CURRENT_STATUS_KEY, idArray);
        }
    }

    @Override
    public SipAgentVo getDetail(Long id) {
        return this.baseMapper.getDetail(id);
    }

    @Override
    public PageInfo<SipAgentVo> getPageList(SipAgentQuery query) {
        startPage(query.getPageIndex(), query.getPageSize(), query.getSortField(), query.getSort());
        List<SipAgentVo> resList = getInfoByQuery(query);
        return new PageInfo<>(resList);
    }

    @Override
    public List<SipAgentVo> getInfoByQuery(SipAgentQuery query) {
        return this.baseMapper.getInfoByQuery(query);
    }

    @Override
    public SipAgentVo getInfoByAgent(String agentNum) {
        return this.baseMapper.getInfoByAgent(agentNum);
    }


    @Override
    public Boolean updateStatus(Long id, Integer status) {
        SipAgent agent = new SipAgent();
        agent.setId(id);
        agent.setStatus(status);
        return updateById(agent);
    }

    @Override
    public List<SipAgentStatusVo> getAgentStatusList(List<Long> agentIds) {
        Map<String, SipAgentStatusVo> cacheMap = redisService.getCacheMap(CacheConstants.AGENT_CURRENT_STATUS_KEY);
        if (CollectionUtil.isNotEmpty(cacheMap) && CollectionUtil.isNotEmpty(agentIds)) {
            return cacheMap.values().stream().filter(agent -> agentIds.contains(agent.getId())).toList();
        }if(CollectionUtil.isNotEmpty(cacheMap) && CollectionUtil.isEmpty(agentIds)){
            return cacheMap.values().stream().toList();
        }
        return List.of();
    }

    @Override
    public void updateOnlineStatus(Long id, Integer onlineStatus, Long timestamp) {
        SipAgent agent = new SipAgent();
        agent.setId(id);
        agent.setOnlineStatus(onlineStatus);
        if (!updateById(agent)) {
            return;
        }

        String agentKey = String.valueOf(id);
        SipAgentStatusVo agentStatus = null;
        if (Boolean.TRUE.equals(redisService.getCacheMapHasKey(
                CacheConstants.AGENT_CURRENT_STATUS_KEY, agentKey))) {
            agentStatus = redisService.getCacheMapValue(CacheConstants.AGENT_CURRENT_STATUS_KEY, agentKey);
        }
        if (agentStatus == null) {
            SipAgentVo detail = getDetail(id);
            agentStatus = new SipAgentStatusVo();
            BeanUtils.copyProperties(detail, agentStatus);
        }
        agentStatus.setOnlineStatus(onlineStatus);
        agentStatus.setStatus(resolveSipAgentStatus(onlineStatus));
        agentStatus.setStatusTime(timestamp);
        redisService.setCacheMapValue(CacheConstants.AGENT_CURRENT_STATUS_KEY, agentKey, agentStatus);
    }

    private int resolveSipAgentStatus(Integer onlineStatus) {
        if (Objects.equals(onlineStatus, 1)) {
            return SipAgentStatusEnum.READY.getCode();
        }
        if (Objects.equals(onlineStatus, 2)) {
            return SipAgentStatusEnum.NOT_READY.getCode();
        }
        if (Objects.equals(onlineStatus, 3)) {
            return SipAgentStatusEnum.NOT_READY_NOT_TALKING.getCode();
        }
        if (Objects.equals(onlineStatus, 4)) {
            return SipAgentStatusEnum.OFF_ON.getCode();
        }
        if (Objects.equals(onlineStatus, 5)) {
            return SipAgentStatusEnum.TALKING_IN.getCode();
        }
        if (Objects.equals(onlineStatus, 6)) {
            return SipAgentStatusEnum.RINGING.getCode();
        }
        if (Objects.equals(onlineStatus, 7)) {
            return SipAgentStatusEnum.TALKING_OUT.getCode();
        }
        return SipAgentStatusEnum.NOT_READY.getCode();
    }

}
