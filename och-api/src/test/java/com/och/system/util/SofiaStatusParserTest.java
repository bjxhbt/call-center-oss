package com.och.system.util;

import com.och.system.domain.vo.fssip.GatewayStatusVo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FreeSWITCH "sofia status gateway" 文本解析测试。
 */
class SofiaStatusParserTest {

    private static final String REGED_OUTPUT = ""
            + "=================================================================================================\n"
            + "Name         gw1\n"
            + "Profile      external\n"
            + "Proxy        sip.provider.com:5060\n"
            + "Realm        sip.provider.com\n"
            + "Username     1001\n"
            + "Register      true\n"
            + "State        REGED\n"
            + "=================================================================================================\n";

    private static final String NOREG_OUTPUT = ""
            + "Name         gw2\n"
            + "Profile      external\n"
            + "State        NOREG\n"
            + "Fail-Reason   Registration failed\n";

    private static final String TRYING_OUTPUT = "Name         gw3\nState        TRYING\n";

    private static final String NOT_FOUND_OUTPUT = "-ERR Gateway not found.\n";

    @Test
    void parseRegistered() {
        GatewayStatusVo vo = SofiaStatusParser.parseGateway(REGED_OUTPUT, "gw1");
        assertEquals("gw1", vo.getName());
        assertEquals("external", vo.getProfile());
        assertEquals("sip.provider.com:5060", vo.getProxy());
        assertEquals(1, vo.getRegister());
        assertEquals("REGED", vo.getState());
        assertEquals(SofiaStatusParser.STATE_REGISTERED, vo.getStateCode());
        assertNull(vo.getFailReason());
    }

    @Test
    void parseFailed_hasFailReason() {
        GatewayStatusVo vo = SofiaStatusParser.parseGateway(NOREG_OUTPUT, "gw2");
        assertEquals(SofiaStatusParser.STATE_FAILED, vo.getStateCode());
        assertNotNull(vo.getFailReason());
        assertTrue(vo.getFailReason().contains("NOREG"));
    }

    @Test
    void parseTrying() {
        GatewayStatusVo vo = SofiaStatusParser.parseGateway(TRYING_OUTPUT, "gw3");
        assertEquals(SofiaStatusParser.STATE_TRYING, vo.getStateCode());
    }

    @Test
    void parseNotFound() {
        GatewayStatusVo vo = SofiaStatusParser.parseGateway(NOT_FOUND_OUTPUT, "missing");
        assertEquals(SofiaStatusParser.STATE_NOT_FOUND, vo.getStateCode());
        assertNotNull(vo.getFailReason());
    }

    @Test
    void mapStateCoversCommonValues() {
        assertEquals(SofiaStatusParser.STATE_REGISTERED, SofiaStatusParser.mapState("REGED"));
        assertEquals(SofiaStatusParser.STATE_REGISTERED, SofiaStatusParser.mapState("REGISTERED"));
        assertEquals(SofiaStatusParser.STATE_TRYING, SofiaStatusParser.mapState("REG_TRYING"));
        assertEquals(SofiaStatusParser.STATE_FAILED, SofiaStatusParser.mapState("FAILED"));
        assertEquals(SofiaStatusParser.STATE_FAILED, SofiaStatusParser.mapState("TIMEOUT"));
        assertEquals(SofiaStatusParser.STATE_UNKNOWN, SofiaStatusParser.mapState("WEIRD"));
        assertEquals(SofiaStatusParser.STATE_UNKNOWN, SofiaStatusParser.mapState(""));
    }

    @Test
    void parseGatewayList_extractsGatewayRows() {
        String table = ""
                + "Name             Type      Data                             State\n"
                + "=================================================================================================\n"
                + "external         profile   rtp                              RUNNING (0)\n"
                + "gw1              gateway   sip.provider.com~5060            REGED\n"
                + "gw2              gateway   1.2.3.4~5060                     NOREG\n";
        var rows = SofiaStatusParser.parseGatewayList(table);
        assertEquals(2, rows.size());
        assertTrue(rows.containsKey("gw1"));
        assertTrue(rows.containsKey("gw2"));
    }
}
