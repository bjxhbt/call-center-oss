package com.och.ivr.handler.node;

import com.alibaba.fastjson.JSONObject;
import com.och.common.config.redis.RedisService;
import com.och.common.constant.EslConstant;
import com.och.common.constant.FlowDataContext;
import com.och.common.domain.CallInfo;
import com.och.common.exception.FlowNodeException;
import com.och.common.utils.StringUtils;
import com.och.esl.client.FsClient;
import com.och.esl.service.CallRecordingService;
import com.och.esl.service.IFlowNoticeService;
import com.och.esl.service.IFsCallCacheService;
import com.och.ivr.domain.vo.FlowNodeVo;
import com.och.ivr.properties.FlowPlaybackNodeProperties;
import com.och.ivr.service.IFlowInfoService;
import com.och.ivr.service.IFlowInstancesService;
import com.och.system.domain.entity.SysFile;
import com.och.system.domain.vo.file.VoiceFileVo;
import com.och.system.service.ISysFileService;
import com.och.system.service.IVoiceFileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.data.redis.RedisStateMachinePersister;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 放音节点处理
 *
 * @author danmo
 * @date 2024-12-26
 */
@Slf4j
@Component("FlowPlaybackHandler")
public class FlowPlaybackHandler extends AbstractIFlowNodeHandler {

    private final IVoiceFileService iVoiceFileService;
    private final CallRecordingService callRecordingService;

    public FlowPlaybackHandler(RedisStateMachinePersister<Object, Object> persister, IFsCallCacheService fsCallCacheService, IFlowNoticeService iFlowNoticeService, IFlowInfoService iFlowInfoService, IFlowInstancesService iFlowInstancesService, FsClient fsClient, RedisService redisService, IVoiceFileService iVoiceFileService, CallRecordingService callRecordingService) {
        super(persister, fsCallCacheService, iFlowNoticeService, iFlowInfoService, iFlowInstancesService, fsClient, redisService);
        this.iVoiceFileService = iVoiceFileService;
        this.callRecordingService = callRecordingService;
    }

    @Override
    public void execute(FlowDataContext flowData) {
        FlowNodeVo flowNode = getFlowNode(flowData.getFlowId(), flowData.getCurrentNodeId());
        if (Objects.isNull(flowNode)){
            throw new FlowNodeException("节点配置错误");
        }
        CallInfo callInfo = fsCallCacheService.getCallInfo(flowData.getCallId());
        if (Objects.isNull(callInfo)){
            throw new FlowNodeException("callInfo is null");
        }
        callInfo.setFlowDataContext(flowData);
        fsCallCacheService.saveCallInfo(callInfo);
        String properties = flowNode.getProperties();
        if (StringUtils.isNotBlank(properties)){
            FlowPlaybackNodeProperties playbackNodeProperties = JSONObject.parseObject(properties, FlowPlaybackNodeProperties.class);
            if (Objects.nonNull(playbackNodeProperties.getInterrupt()) && playbackNodeProperties.getInterrupt()){
                setPlaybackTerminators(flowData, playbackNodeProperties.getInterruptKeys());
            }else {
                setChannelVariable(flowData, "playback_terminators", "none");
            }
            setChannelVariable(flowData, "playback_delimiter", "!");
            armRecordingAfterPlayback(playbackNodeProperties, flowData);
            if (playbackNodeProperties.getPlaybackType() == 1){
                Long fileId = resolveFileId(playbackNodeProperties, flowData);
                VoiceFileVo voiceFile = iVoiceFileService.getDetail(fileId);
                if (Objects.isNull(voiceFile)) {
                    throw new FlowNodeException("voice file is null: " + fileId);
                }
                if (StringUtils.isNotBlank(playbackNodeProperties.getRememberFileVariable())) {
                    FlowVariables.put(redisService, flowData.getInstanceId(),
                            playbackNodeProperties.getRememberFileVariable(), fileId);
                }
                String playPath = StringUtils.isNotBlank(voiceFile.getFilePath()) ? voiceFile.getFilePath() : voiceFile.getFileName();
                if (StringUtils.isBlank(playPath)) {
                    throw new FlowNodeException("voice file path is blank: " + fileId);
                }
                StringBuilder fileName = new StringBuilder(playPath);
                int playNum = playbackNodeProperties.getNum() != null ? playbackNodeProperties.getNum() : 1;
                if(playNum > 1){
                    for (int i = 1; i < playNum; i++){
                        fileName.append(EslConstant.EXCLAMATION).append(playPath);
                    }
                }
                log.info("IVR播放文件: callId:{}, uniqueId:{}, file:{}", flowData.getCallId(), flowData.getUniqueId(), fileName);
                fsClient.playFile(flowData.getAddress(), flowData.getUniqueId(), fileName.toString());
            }else if (playbackNodeProperties.getPlaybackType() == 2){
                //tts 播放
                String ttsEngine = flowData.getTtsEngine();
                if(StringUtils.isNotBlank(ttsEngine)){
                    String ttsVoice = flowData.getTtsVoice();
                    fsClient.sendArgs(flowData.getAddress(), flowData.getUniqueId(), EslConstant.SET, EslConstant.TTS_ENGINE+ ttsEngine);
                    fsClient.sendArgs(flowData.getAddress(), flowData.getUniqueId(), EslConstant.SET, EslConstant.TTS_VOICE+ ttsVoice);
                }
                int ttsNum = playbackNodeProperties.getNum() != null ? playbackNodeProperties.getNum() : 1;
                StringBuilder fileName = new StringBuilder("say:" + playbackNodeProperties.getContent() + "'");
                if (ttsNum > 1){
                    for (int i = 1; i < ttsNum; i++){
                        fileName.append(EslConstant.EXCLAMATION + "say:").append(playbackNodeProperties.getContent()).append("'");
                    }
                }
                fsClient.playFile(flowData.getAddress(), flowData.getUniqueId(), fileName.toString());
            }
        }

    }

    private void armRecordingAfterPlayback(FlowPlaybackNodeProperties properties, FlowDataContext flowData) {
        if (Boolean.TRUE.equals(properties.getStartRecordingAfterPlayback())) {
            callRecordingService.armAfterPlayback(flowData.getCallId(), flowData.getUniqueId());
        }
    }

    private void setPlaybackTerminators(FlowDataContext flowData, String configuredKeys) {
        String interruptKeys = StringUtils.isBlank(configuredKeys) || "any".equalsIgnoreCase(configuredKeys)
                ? "0123456789*#" : configuredKeys;
        setChannelVariable(flowData, "playback_terminators", interruptKeys);
    }

    private void setChannelVariable(FlowDataContext flowData, String name, String value) {
        fsClient.sendSyncMsg(flowData.getAddress(), EslConstant.SETVAR,
                flowData.getUniqueId() + " " + name + " " + value);
    }

    private Long resolveFileId(FlowPlaybackNodeProperties properties, FlowDataContext flowData) {
        if (StringUtils.isBlank(properties.getFileIdVariable())) {
            if (properties.getFileId() == null) {
                throw new FlowNodeException("voice file id is null");
            }
            return properties.getFileId();
        }
        String variableValue = FlowVariables.get(redisService, flowData.getInstanceId(), properties.getFileIdVariable());
        if (StringUtils.isBlank(variableValue)) {
            throw new FlowNodeException("voice file variable is blank: " + properties.getFileIdVariable());
        }
        try {
            return Long.valueOf(variableValue);
        } catch (NumberFormatException e) {
            throw new FlowNodeException("invalid voice file variable: " + properties.getFileIdVariable());
        }
    }

    @Override
    public void businessHandler(String event, FlowDataContext flowData) throws FlowNodeException {
        if (StringUtils.isBlank(event)) {
            return;
        }
        FlowNodeVo flowNode = getFlowNode(flowData.getFlowId(), flowData.getCurrentNodeId());
        FlowPlaybackNodeProperties playbackNodeProperties = flowNode == null ? null
                : JSONObject.parseObject(flowNode.getProperties(), FlowPlaybackNodeProperties.class);
        if (playbackNodeProperties != null && StringUtils.isNotBlank(playbackNodeProperties.getDtmfForwardVariable())) {
            FlowVariables.put(redisService, flowData.getInstanceId(),
                    playbackNodeProperties.getDtmfForwardVariable(), event);
            iFlowNoticeService.notice(2, "next", flowData);
            return;
        }
        if ("#".equals(event)) {
            execute(flowData);
            return;
        }
        if ("*".equals(event)) {
            iFlowNoticeService.notice(2, "next", flowData);
            return;
        }
        iFlowNoticeService.notice(2, "next_" + event, flowData);
    }
}
