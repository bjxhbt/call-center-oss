package com.och.esl.client;

import com.och.esl.propeties.FsClientProperties;
import com.och.system.service.IFsConfigService;
import org.freeswitch.esl.client.transport.message.EslMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FsClientChannelCallIdTest {

    @Test
    void readsPrimaryOchCallIdVariable() {
        ChannelVariableFsClient client = new ChannelVariableFsClient("10001");

        assertThat(client.getChannelCallId("127.0.0.1:8021", "channel-uuid"))
                .isEqualTo("10001");
    }

    @Test
    void fallsBackWhenPrimaryVariableIsUndefined() {
        ChannelVariableFsClient client = new ChannelVariableFsClient("_undef_", "10002");

        assertThat(client.getChannelCallId("127.0.0.1:8021", "channel-uuid"))
                .isEqualTo("10002");
    }

    private static final class ChannelVariableFsClient extends FsClient {
        private final Queue<String> responses;

        private ChannelVariableFsClient(String... responses) {
            super(mock(IFsConfigService.class), new FsClientProperties());
            this.responses = new ArrayDeque<>(Arrays.asList(responses));
        }

        @Override
        public EslMessage sendSyncMsg(String address, String cmd, String args) {
            EslMessage response = mock(EslMessage.class);
            when(response.getBodyLines()).thenReturn(java.util.List.of(responses.remove()));
            return response;
        }
    }
}
