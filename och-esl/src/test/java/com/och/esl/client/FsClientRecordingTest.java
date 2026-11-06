package com.och.esl.client;

import com.och.esl.propeties.FsClientProperties;
import com.och.system.service.IFsConfigService;
import org.freeswitch.esl.client.transport.message.EslMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FsClientRecordingTest {

    @Test
    void configuresRecordingBeforeStartingUuidRecord() {
        EslMessage response = mock(EslMessage.class);
        when(response.getBodyLines()).thenReturn(List.of("+OK Success"));
        RecordingFsClient client = new RecordingFsClient(response);

        boolean started = client.record("127.0.0.1:8021", 123L, "caller-uuid",
                "/usr/local/freeswitch/recordings/2026-08-21/123.wav");

        assertThat(started).isTrue();
        assertThat(client.commands).containsExactly(
                "uuid_setvar|caller-uuid record_sample_rate 8000",
                "uuid_setvar|caller-uuid RECORD_STEREO true",
                "uuid_setvar|caller-uuid RECORD_CHECK_BRIDGE true",
                "uuid_setvar|caller-uuid enable_file_write_buffering false",
                "uuid_setvar|caller-uuid recording_follow_transfer true",
                "uuid_record|caller-uuid start /usr/local/freeswitch/recordings/2026-08-21/123.wav"
        );
    }

    @Test
    void reportsUuidRecordRejection() {
        EslMessage response = mock(EslMessage.class);
        when(response.getBodyLines()).thenReturn(List.of("-ERR Cannot record session!"));
        RecordingFsClient client = new RecordingFsClient(response);

        boolean started = client.record("127.0.0.1:8021", 123L, "caller-uuid", "/tmp/123.wav");

        assertThat(started).isFalse();
    }

    private static final class RecordingFsClient extends FsClient {
        private final List<String> commands = new ArrayList<>();
        private final EslMessage response;

        private RecordingFsClient(EslMessage response) {
            super(mock(IFsConfigService.class), new FsClientProperties());
            this.response = response;
            this.sampleRate = "8000";
        }

        @Override
        public EslMessage sendSyncMsg(String address, String cmd, String args) {
            commands.add(cmd + "|" + args);
            return response;
        }
    }
}
