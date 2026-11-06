package com.och.esl.service;

import com.och.system.domain.vo.playback.PlaybackStatusInfo;

/**
 * 通道播放状态注册表(内存，按通道 UUID 存取)。
 * <p>
 * 由播放服务(发起/停止)与 ESL 放音事件(PLAYBACK_START/PLAYBACK_STOP/EXECUTE_COMPLETE)共同维护，
 * 对外提供按 UUID 查询播放状态的能力。
 *
 * @author och
 */
public interface IPlaybackRegistry {

    /** 发起播放请求 */
    void requested(String uuid, String file);

    /** 标记正在播放(来自 PLAYBACK_START 事件) */
    void playing(String uuid);

    /** 标记播放完成 */
    void completed(String uuid);

    /** 标记已停止 */
    void stopped(String uuid);

    /** 标记播放失败(校验失败或通道异常)，不挂断通话 */
    void failed(String uuid, String reason);

    /** 查询播放状态 */
    PlaybackStatusInfo get(String uuid);

    /** 移除 */
    void remove(String uuid);
}
