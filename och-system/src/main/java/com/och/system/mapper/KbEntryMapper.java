package com.och.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.och.system.domain.entity.KbEntry;
import com.och.system.domain.query.kb.KbEntryQuery;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Mapper
public interface KbEntryMapper extends BaseMapper<KbEntry> {

    List<KbEntry> getList(KbEntryQuery query);
}
