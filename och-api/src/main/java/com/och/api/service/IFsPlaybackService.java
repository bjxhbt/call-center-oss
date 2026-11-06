package com.och.api.service;

import com.och.system.domain.vo.playback.PlaybackStatusInfo;

/**
 * FreeSWITCH 通道播放服务：按通道 UUID 播放、停止文件，并维护播放状态。
 * <p>
 * 播放失败仅记录状态，不挂断当前通话(保障通话稳定)。
 *
 * @author och
 */
public interface IFsPlaybackService {

    /**
     * 在指定通道上播放文件。
     *
     * @param uuid 通道UUID
     * @param file 文件(FS 可访问路径或 say: 语法)
     * @param loop 循环次数(为 null 或 &lt;=0 表示单次播放)
     * @return 播放状态
     */
    PlaybackStatusInfo play(String uuid, String file, Integer loop);

    /**
     * 停止指定通道的播放。
     */
    PlaybackStatusInfo stop(String uuid);

    /**
     * 查询播放状态。
     */
    PlaybackStatusInfo status(String uuid);
}
