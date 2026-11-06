package com.och.ai.tts.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.och.ai.tts.entity.TtsTask;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

/**
 * tts_task 表 Mapper。仅用 BaseMapper + LambdaQueryWrapper，不写自定义 XML。
 */
@Mapper
@Repository
public interface TtsTaskMapper extends BaseMapper<TtsTask> {
}
