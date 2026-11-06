package com.och.api.handler;

import com.och.system.domain.entity.FsConfig;
import com.och.system.service.IFsConfigService;
import com.och.system.service.IFsDialplanService;
import com.och.system.domain.vo.dialplan.FsDialplanVo;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FsSipGatewayContainerTest {
    @Test
    void containerBindsLocallyAndAdvertisesReachableHost() {
        var handler = new FsSipGatewayXmlCurlHandler();
        ReflectionTestUtils.setField(handler, "containerNetwork", true);
        ReflectionTestUtils.setField(handler, "advertisedIp", "192.0.2.10");
        assertThat((String) ReflectionTestUtils.invokeMethod(handler, "getFsBindIp")).isEqualTo("$${local_ip_v4}");
        assertThat((String) ReflectionTestUtils.invokeMethod(handler, "getAdvertisedIp", "$${local_ip_v4}")).isEqualTo("192.0.2.10");
    }

    @Test
    void existingNonContainerDeploymentsKeepTheirAddressConvention() {
        var handler = new FsSipGatewayXmlCurlHandler();
        var service = mock(IFsConfigService.class);
        var node = new FsConfig();
        node.setIp("192.0.2.11");
        when(service.getList(any())).thenReturn(List.of(node));
        ReflectionTestUtils.setField(handler, "iFsConfigService", service);
        assertThat((String) ReflectionTestUtils.invokeMethod(handler, "getFsBindIp")).isEqualTo("192.0.2.11");
        assertThat((String) ReflectionTestUtils.invokeMethod(handler, "getAdvertisedIp", "192.0.2.11")).isEqualTo("192.0.2.11");
    }

    @Test
    void demoDialplanCanBeRenderedThroughTheActualXmlCurlHandler() {
        var handler = new FsDialplanXmlCurlHandler();
        var service = mock(IFsDialplanService.class);
        var extension = new FsDialplanVo();
        extension.setName("demo-internal-extensions");
        extension.setType("xml");
        extension.setContent("<extension><condition field=\"destination_number\" expression=\"^(100[12])$\"><action application=\"bridge\" data=\"user/$1@$${domain}\"/></condition></extension>");
        when(service.getList(any())).thenReturn(List.of(extension));
        ReflectionTestUtils.setField(handler, "iFsDialplanService", service);
        String xml = handler.eventHandle(null);
        assertThat(xml).contains("demo-internal-extensions", "destination_number", "user/$1@$${domain}")
                .containsIgnoringCase("bridge");
    }
}
