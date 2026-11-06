package com.och.ai.asr.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.och.ai.asr.entity.CallTranscriptSegment;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

/**
 * call_transcript_segment 表 Mapper。仅用 BaseMapper + LambdaQueryWrapper，不写自定义 XML。
 */
@Mapper
@Repository
public interface CallTranscriptSegmentMapper extends BaseMapper<CallTranscriptSegment> {
}
