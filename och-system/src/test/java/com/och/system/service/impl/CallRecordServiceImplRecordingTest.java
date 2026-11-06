package com.och.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.och.system.domain.entity.CallRecord;
import com.och.system.mapper.CallRecordMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallRecordServiceImplRecordingTest {

    private CallRecordMapper mapper;
    private CallRecordServiceImpl service;

    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        assistant.setCurrentNamespace("com.och.system.mapper.CallRecordMapper");
        TableInfoHelper.initTableInfo(assistant, CallRecord.class);
        mapper = mock(CallRecordMapper.class);
        service = new CallRecordServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        when(mapper.update(isNull(), org.mockito.ArgumentMatchers.<Wrapper<CallRecord>>any()))
                .thenReturn(1);
    }

    @Test
    void uploadedStatusIsIncludedInUpdateSet() {
        boolean updated = service.updateRecordingFields(
                "10001", "/recordings/10001.wav", "http://rustfs/call-recordings/10001.wav", 12, 2);

        assertThat(updated).isTrue();
        ArgumentCaptor<Wrapper<CallRecord>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).update(isNull(), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSet()).contains("record_status");
        AbstractWrapper<?, ?, ?> wrapper = (AbstractWrapper<?, ?, ?>) wrapperCaptor.getValue();
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(2, "http://rustfs/call-recordings/10001.wav");
    }

    @Test
    void uploadingStatusIsIncludedInUpdateSet() {
        service.updateRecordingFields("10002", "/recordings/10002.wav", null, 8, 1);

        ArgumentCaptor<Wrapper<CallRecord>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).update(isNull(), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSet()).contains("record_status");
        AbstractWrapper<?, ?, ?> wrapper = (AbstractWrapper<?, ?, ?>) wrapperCaptor.getValue();
        assertThat(wrapper.getParamNameValuePairs().values()).contains(1);
    }
}
