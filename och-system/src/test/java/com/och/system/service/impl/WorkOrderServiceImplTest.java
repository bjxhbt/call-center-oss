package com.och.system.service.impl;

import com.och.system.domain.entity.WorkOrder;
import com.och.system.mapper.CallRecordMapper;
import com.och.system.mapper.WorkOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkOrderServiceImplTest {

    private WorkOrderMapper workOrderMapper;
    private WorkOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        workOrderMapper = mock(WorkOrderMapper.class);
        service = new WorkOrderServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", workOrderMapper);
        ReflectionTestUtils.setField(service, "callRecordMapper", mock(CallRecordMapper.class));
        when(workOrderMapper.insert(any(WorkOrder.class))).thenReturn(1);
    }

    @Test
    void savesCompletedStatusAndFinishTime() {
        WorkOrder incoming = new WorkOrder();
        incoming.setWorkOrderType("咨询");
        incoming.setAcceptanceContent("已记录并处理来电问题");
        incoming.setStatus(2);

        service.saveDetails(incoming);

        ArgumentCaptor<WorkOrder> captor = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrderMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(2);
        assertThat(captor.getValue().getFinishTime()).isNotNull();
    }
}
