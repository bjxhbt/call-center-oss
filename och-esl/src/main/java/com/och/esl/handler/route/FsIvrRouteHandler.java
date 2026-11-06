package com.och.esl.handler.route;

import cn.hutool.core.date.DateUtil;
import com.och.common.annotation.EslRouteName;
import com.och.common.domain.CallInfo;
import com.och.common.domain.CallInfoDetail;
import com.och.common.enums.RouteTypeEnum;
import com.och.esl.service.IFlowNoticeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author danmo
 * @date 2023-11-10 17:20
 **/
@RequiredArgsConstructor
@EslRouteName(RouteTypeEnum.IVR)
@Component
@Slf4j
public class FsIvrRouteHandler extends FsAbstractRouteHandler {

    private final IFlowNoticeService iFlowNoticeService;

    @Override
    public void handler(String address, CallInfo callInfo, String uniqueId, String flowId) {
        log.info("转ivr callId:{} transfer to {}", callInfo.getCallId(), flowId);
        // IVR媒体预热: 解决"第一次拨打IVR无声"问题
        // 根因: 首次SPEAK时MRCP client需新建到och-mrcp的SIP+MRCP+RTP session(冷启动~1-2s),
        //       音频到达晚于FS playback窗口导致首句被丢; 第二次拨号session已预热才有声。
        // 方案: 先answer建立媒体通道(RTP auto-fix在此期间完成), 再用极短TTS预热MRCP session,
        //       之后IVR正式节点的say:TTS即可即时出声。
        fsClient.answer(address, uniqueId);
        // 设TTS引擎, 与IVR放音节点一致(mod_unimrcp profile=och)
        fsClient.sendArgs(address, uniqueId, "set", "tts_engine=unimrcp");
        fsClient.sendArgs(address, uniqueId, "set", "tts_voice=och");
        // 预热TTS: 单字符"嗯", 触发MRCP session建立; 即使这句本身丢音, 后续IVR出声正常
        fsClient.playFile(address, uniqueId, "silence_stream://200'");

        CallInfoDetail detail = new CallInfoDetail();
        detail.setCallId(callInfo.getCallId());
        detail.setStartTime(DateUtil.current());
        detail.setOrderNum(callInfo.getDetailList() == null ? 0 : callInfo.getDetailList().size() + 1);
        detail.setTransferType(2);
        detail.setTransferId(Long.parseLong(flowId));
        iFlowNoticeService.notice(address, callInfo.getCallId(),uniqueId, Long.parseLong(flowId));
        callInfo.addDetailList(detail);
        fsCallCacheService.saveCallInfo(callInfo);
    }
}
