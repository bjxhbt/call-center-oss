package com.och.ai.tts.service;

import com.och.ai.tts.entity.TtsFile;

import java.util.List;

/**
 * TTS 文件记录持久化（tts_file）。
 */
public interface TtsFileService {

    /** 持久化一条记录（新增或按 cacheKey 更新）。返回主键。 */
    Long save(TtsFile file);

    /** 按 cacheKey 查询 SUCCESS 记录（缓存命中）。 */
    TtsFile findSuccessByCacheKey(String cacheKey);

    /** 按文本指纹查询最近一次成功记录（主用失败时的降级）。 */
    TtsFile findLatestSuccessByTextDigest(String textDigest);

    TtsFile getById(Long id);

    /** 条件查询（provider/text 模糊），不分页。 */
    List<TtsFile> list(String provider, String keyword);

    /**
     * 删除：先校验 refCount，&gt;0 抛出引用占用；否则软删记录并删除磁盘文件。
     *
     * @return 是否物理删除了磁盘文件
     */
    boolean delete(Long id);

    /** 增减引用计数（创建任务播放时 +1，结束时可 -1，最低 0）。 */
    int adjustRefCount(Long id, int delta);

    /** 将最近一次成功记录用于降级返回时，刷新其时间（可选）。 */
    void touch(Long id);
}
