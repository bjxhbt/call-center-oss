package com.och.esl.handler.call;

import com.och.common.annotation.EslProcessName;
import com.och.common.domain.CallInfo;
import com.och.common.domain.ChannelInfo;
import com.och.common.enums.ProcessEnum;
import com.och.esl.service.IBridgeStateCache;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.freeswitch.esl.client.transport.event.EslEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

/**
 * 桥接
 *
 * @author danmo
 * @date 2023-10-23 17:05
 **/
@EslProcessName(ProcessEnum.CALL_BRIDGE)
@Component
@Slf4j
public class FsCallBridgeProcess extends FsAbstractCallProcess {

    @Autowired
    private IBridgeStateCache bridgeStateCache;

    @Override
    public void handler(String address, EslEvent event, CallInfo callInfo, ChannelInfo lfsChannelInfo) {
        log.info("开始桥接电话: callId:{} caller:{} called:{} uniqueId:{}, otherUniqueId:{}", callInfo.getCallId(), callInfo.getCaller(), callInfo.getCallee(), lfsChannelInfo.getOtherUniqueId(), lfsChannelInfo.getUniqueId());
        String uniqueId = lfsChannelInfo.getUniqueId();
        String otherUniqueId = lfsChannelInfo.getOtherUniqueId();
        Map<String, ChannelInfo> channelMap = callInfo.getChannelMap();
        ChannelInfo channel = channelMap == null ? null : channelMap.get(otherUniqueId);
        ChannelInfo otherChannel = channelMap == null ? null : channelMap.get(uniqueId);
        if (otherChannel == null) {
            otherChannel = lfsChannelInfo;
        }
        if (channel == null) {
            channel = findPeerChannel(channelMap, uniqueId);
            if (channel != null) {
                otherUniqueId = channel.getUniqueId();
            }
        }
        if (StringUtils.isBlank(uniqueId) || StringUtils.isBlank(otherUniqueId) || channel == null || otherChannel == null) {
            log.error("bridge call failed, missing channel info. callId:{} uniqueId:{} otherUniqueId:{} channelMap:{}", callInfo.getCallId(), uniqueId, otherUniqueId, channelMap == null ? null : channelMap.keySet());
            fsClient.killCall(address, callInfo.getCallId(), uniqueId);
            return;
        }
        lfsChannelInfo.setOtherUniqueId(otherUniqueId);
        otherChannel.setOtherUniqueId(otherUniqueId);
        channel.setOtherUniqueId(uniqueId);

        //fsClient.record(address, callInfo.getCallId(), lfsChannelInfo.getUniqueId(), callInfo.getRecord());

        callInfo.setChannelInfoMap(otherUniqueId,channel);
        callInfo.setChannelInfoMap(uniqueId,otherChannel);

        // 幂等守卫: 同一 callId 只允许发起一次 uuid_bridge，防止坐席多注册/重复 ANSWER 导致重复桥接
        // (重复 uuid_bridge 会触发 "接听后立即挂断" 问题)
        if (!bridgeStateCache.markBridging(callInfo.getCallId())) {
            log.warn("跳过重复桥接(同一 callId 已在桥接中) callId:{} uniqueId:{}", callInfo.getCallId(), uniqueId);
            return;
        }

        lfsCallCacheService.saveCallInfo(callInfo);
        fsClient.bridgeCall(address, callInfo.getCallId(), otherUniqueId, uniqueId);
    }

    private ChannelInfo findPeerChannel(Map<String, ChannelInfo> channelMap, String uniqueId) {
        if (channelMap == null || channelMap.isEmpty()) {
            return null;
        }
        ChannelInfo linkedChannel = channelMap.values().stream()
                .filter(Objects::nonNull)
                .filter(channel -> StringUtils.isNotBlank(channel.getUniqueId()))
                .filter(channel -> !StringUtils.equals(channel.getUniqueId(), uniqueId))
                .filter(channel -> StringUtils.equals(channel.getOtherUniqueId(), uniqueId))
                .findFirst()
                .orElse(null);
        if (linkedChannel != null) {
            return linkedChannel;
        }
        ChannelInfo inboundChannel = channelMap.values().stream()
                .filter(Objects::nonNull)
                .filter(channel -> StringUtils.isNotBlank(channel.getUniqueId()))
                .filter(channel -> !StringUtils.equals(channel.getUniqueId(), uniqueId))
                .filter(channel -> Objects.equals(channel.getDirectionType(), 1))
                .findFirst()
                .orElse(null);
        if (inboundChannel != null) {
            return inboundChannel;
        }
        return channelMap.values().stream()
                .filter(Objects::nonNull)
                .filter(channel -> StringUtils.isNotBlank(channel.getUniqueId()))
                .filter(channel -> !StringUtils.equals(channel.getUniqueId(), uniqueId))
                .filter(channel -> !isGeneratedUuid(channel.getUniqueId()))
                .findFirst()
                .orElseGet(() -> channelMap.values().stream()
                        .filter(Objects::nonNull)
                        .filter(channel -> StringUtils.isNotBlank(channel.getUniqueId()))
                        .filter(channel -> !StringUtils.equals(channel.getUniqueId(), uniqueId))
                        .findFirst()
                        .orElse(null));
    }

    private boolean isGeneratedUuid(String uniqueId) {
        return StringUtils.length(uniqueId) == 32 && StringUtils.isNumeric(uniqueId);
    }
}
