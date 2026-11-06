
package com.och.esl.handler.esl;

import com.alibaba.fastjson.JSONObject;
import com.och.common.annotation.EslEventName;
import com.och.common.constant.EslEventNames;
import com.och.esl.factory.AbstractFsEslEventHandler;
import com.och.esl.service.IPlaybackRegistry;
import com.och.esl.utils.EslEventUtil;
import lombok.extern.slf4j.Slf4j;
import org.freeswitch.esl.client.transport.event.EslEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 放音开始
 * @author danmo
 * @date 2023年09月18日 19:03
 */
@Slf4j
@EslEventName(EslEventNames.PLAYBACK_START)
@Component
public class FsChannelPlaybackStartEslEventHandler extends AbstractFsEslEventHandler {

    @Autowired
    private IPlaybackRegistry playbackRegistry;

    @Override
    public void handleEslEvent(String address, EslEvent event) {
        log.info("放音开始 EslEvent:{}.", JSONObject.toJSONString(event));
        String uuid = EslEventUtil.getUniqueId(event);
        if (uuid != null) {
            playbackRegistry.playing(uuid);
        }
    }
}
