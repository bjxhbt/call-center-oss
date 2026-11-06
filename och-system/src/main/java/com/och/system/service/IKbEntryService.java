package com.och.system.service;

import com.github.pagehelper.PageInfo;
import com.och.common.base.IBaseService;
import com.och.system.domain.entity.KbEntry;
import com.och.system.domain.query.kb.KbEntryAddQuery;
import com.och.system.domain.query.kb.KbEntryQuery;

import java.util.List;

public interface IKbEntryService extends IBaseService<KbEntry> {

    void add(KbEntryAddQuery query);

    void edit(KbEntryAddQuery query);

    void delete(KbEntryQuery query);

    KbEntry getDetail(Long id);

    PageInfo<KbEntry> pageList(KbEntryQuery query);

    List<KbEntry> list(KbEntryQuery query);
}
