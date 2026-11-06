package com.och.ai.tts.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.och.ai.tts.entity.TtsFile;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

/**
 * tts_file 表 Mapper。仅用 BaseMapper + LambdaQueryWrapper，不写自定义 XML。
 */
@Mapper
@Repository
public interface TtsFileMapper extends BaseMapper<TtsFile> {
}
