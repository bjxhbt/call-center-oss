package com.och.esl.service.impl;

import com.och.esl.event.ChannelPlaybackEvent;
import com.och.esl.service.IPlaybackRegistry;
import com.och.system.domain.vo.playback.PlaybackStatusInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于内存 ConcurrentHashMap 的播放状态注册表实现。
 * <p>
 * 进程级状态，重启后丢失(播放为短时态，可接受)。如需跨实例可后续替换为 Redis 实现。
 * 在 PLAYING/COMPLETED/STOPPED/FAILED 状态变化时发布 {@link ChannelPlaybackEvent}，
 * 供 TTS 播放适配器回写任务状态(方案 9.6/8.8)。
 *
 * @author och
 */
@Service
public class PlaybackRegistryImpl implements IPlaybackRegistry {

    private final ConcurrentHashMap<String, PlaybackStatusInfo> store = new ConcurrentHashMap<>();

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Override
    public void requested(String uuid, String file) {
        PlaybackStatusInfo info = newInfo(uuid, file, PlaybackStatusInfo.STATE_REQUESTED, null);
        store.put(uuid, info);
    }

    @Override
    public void playing(String uuid) {
        store.computeIfPresent(uuid, (k, v) -> {
            v.setState(PlaybackStatusInfo.STATE_PLAYING);
            v.setUpdateTime(System.currentTimeMillis());
            return v;
        });
        publish(uuid, PlaybackStatusInfo.STATE_PLAYING, null);
    }

    @Override
    public void completed(String uuid) {
        store.computeIfPresent(uuid, (k, v) -> {
            v.setState(PlaybackStatusInfo.STATE_COMPLETED);
            v.setUpdateTime(System.currentTimeMillis());
            return v;
        });
        publish(uuid, PlaybackStatusInfo.STATE_COMPLETED, null);
    }

    @Override
    public void stopped(String uuid) {
        store.computeIfPresent(uuid, (k, v) -> {
            v.setState(PlaybackStatusInfo.STATE_STOPPED);
            v.setUpdateTime(System.currentTimeMillis());
            return v;
        });
        publish(uuid, PlaybackStatusInfo.STATE_STOPPED, null);
    }

    @Override
    public void failed(String uuid, String reason) {
        PlaybackStatusInfo info = newInfo(uuid, null, PlaybackStatusInfo.STATE_FAILED, reason);
        store.put(uuid, info);
        publish(uuid, PlaybackStatusInfo.STATE_FAILED, reason);
    }

    @Override
    public PlaybackStatusInfo get(String uuid) {
        return uuid == null ? null : store.get(uuid);
    }

    @Override
    public void remove(String uuid) {
        if (uuid != null) {
            store.remove(uuid);
        }
    }

    private void publish(String uuid, String state, String reason) {
        if (uuid == null || eventPublisher == null) {
            return;
        }
        PlaybackStatusInfo info = store.get(uuid);
        String file = info == null ? null : info.getFile();
        try {
            eventPublisher.publishEvent(ChannelPlaybackEvent.builder()
                    .uuid(uuid)
                    .file(file)
                    .state(state)
                    .reason(reason)
                    .timestamp(System.currentTimeMillis())
                    .build());
        } catch (Exception e) {
            // 事件发布失败不影响播放主流程
        }
    }

    private PlaybackStatusInfo newInfo(String uuid, String file, String state, String failReason) {
        PlaybackStatusInfo info = new PlaybackStatusInfo();
        info.setUuid(uuid);
        info.setFile(file);
        info.setState(state);
        info.setFailReason(failReason);
        info.setUpdateTime(System.currentTimeMillis());
        return info;
    }
}
