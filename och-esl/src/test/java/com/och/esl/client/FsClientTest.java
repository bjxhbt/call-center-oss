package com.och.esl.client;

import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FsClientTest {

    @Test
    void calculatesChannelAgeFromCreatedEpoch() {
        JSONObject row = new JSONObject();
        row.put("created_epoch", "1000");

        assertThat(FsClient.channelAgeSeconds(row, 1120L)).isEqualTo(120L);
    }

    @Test
    void returnsNegativeAgeWhenCreatedEpochIsMissingOrInvalid() {
        assertThat(FsClient.channelAgeSeconds(new JSONObject(), 1120L)).isEqualTo(-1L);

        JSONObject invalidRow = new JSONObject();
        invalidRow.put("created_epoch", "invalid");
        assertThat(FsClient.channelAgeSeconds(invalidRow, 1120L)).isEqualTo(-1L);
    }
}
