package com.och.ai.asr.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.och.ai.asr.entity.CallTranscript;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

/**
 * call_transcript 表 Mapper。仅用 BaseMapper + LambdaQueryWrapper，不写自定义 XML。
 */
@Mapper
@Repository
public interface CallTranscriptMapper extends BaseMapper<CallTranscript> {
}
