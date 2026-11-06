package com.och.esl.service;

import com.och.system.domain.entity.CallRecord;
import com.och.system.service.ICallRecordService;
import com.och.file.config.S3StorageConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecordingUploadServiceTest {

    private RecordingUploadService uploadService;
    private ICallRecordService callRecordService;

    @BeforeEach
    void setUp() {
        uploadService = new RecordingUploadService();
        callRecordService = mock(ICallRecordService.class);
        ReflectionTestUtils.setField(uploadService, "callRecordService", callRecordService);
        S3StorageConfig storageConfig = new S3StorageConfig();
        storageConfig.setBucket("call-recordings");
        ReflectionTestUtils.setField(uploadService, "storageConfig", storageConfig);
    }

    @Test
    void verifiesUploadedStatusAndUrlAfterDatabaseUpdate() {
        String recordUrl = "http://rustfs/call-recordings/2026-08-21/recording.wav";
        CallRecord persisted = new CallRecord();
        persisted.setCallId("10001");
        persisted.setRecordStatus(2);
        persisted.setRecordUrl(recordUrl);
        when(callRecordService.updateRecordingFields(
                "10001", "/recordings/10001.wav", recordUrl, 12, 2)).thenReturn(true);
        when(callRecordService.getByCallId("10001")).thenReturn(persisted);

        assertThat(uploadService.persistUploadedRecording(
                "10001", "/recordings/10001.wav", recordUrl, 12)).isTrue();
        verify(callRecordService).getByCallId("10001");
    }

    @Test
    void extractsObjectKeyFromRustFsUrl() {
        assertThat(uploadService.extractObjectKey(
                "http://127.0.0.1:9000/call-recordings/2026-08-21/e3f091.wav"))
                .isEqualTo("2026-08-21/e3f091.wav");
    }
}
