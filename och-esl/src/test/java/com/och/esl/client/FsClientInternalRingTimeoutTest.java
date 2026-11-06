package com.och.esl.client;

import com.och.esl.propeties.FsClientProperties;
import com.och.esl.service.IFsEslEventService;
import com.och.system.service.IFsConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class FsClientInternalRingTimeoutTest {

    private FsClient fsClient;

    @BeforeEach
    void setUp() {
        fsClient = new FsClient(Mockito.mock(IFsConfigService.class), new FsClientProperties());
        ReflectionTestUtils.setField(fsClient, "internalRingTimeoutSeconds", 120);
        ReflectionTestUtils.setField(fsClient, "ILfsEslEventService", Mockito.mock(IFsEslEventService.class));
    }

    @AfterEach
    void tearDown() {
        fsClient.destroy();
    }

    @Test
    void usesConfiguredMinimumWhenRequestedTimeoutIsMissingOrTooShort() {
        assertThat(fsClient.resolveInternalRingTimeout(null)).isEqualTo(120);
        assertThat(fsClient.resolveInternalRingTimeout(10)).isEqualTo(120);
    }

    @Test
    void keepsLongerRequestedTimeout() {
        assertThat(fsClient.resolveInternalRingTimeout(180)).isEqualTo(180);
    }
}
