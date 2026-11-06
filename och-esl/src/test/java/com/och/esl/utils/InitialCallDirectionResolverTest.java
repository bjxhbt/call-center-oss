package com.och.esl.utils;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InitialCallDirectionResolverTest {

    @Test
    void externalProfileWinsOverJsSipUserAgent() {
        Map<String, String> headers = Map.of(
                EslEventUtil.VARIABLE_SOFIA_PROFILE_NAME, "external",
                EslEventUtil.VARIABLE_SIP_USER_AGENT, "JsSIP 3.13.8",
                EslEventUtil.CALLER_DIRECTION, "inbound");

        assertEquals(InitialCallDirectionResolver.Decision.INBOUND,
                InitialCallDirectionResolver.resolve(headers, false, false));
    }

    @Test
    void publicContextIsInbound() {
        Map<String, String> headers = Map.of(
                EslEventUtil.CALLER_CONTEXT, "public",
                EslEventUtil.VARIABLE_SIP_USER_AGENT, "JsSIP 3.13.8");

        assertEquals(InitialCallDirectionResolver.Decision.INBOUND,
                InitialCallDirectionResolver.resolve(headers, false, false));
    }

    @Test
    void activeInboundRouteWinsForInternalCaller() {
        Map<String, String> headers = Map.of(
                EslEventUtil.VARIABLE_SOFIA_PROFILE_NAME, "internal",
                EslEventUtil.VARIABLE_SIP_USER_AGENT, "MicroSIP/3.22.7",
                EslEventUtil.CALLER_DIRECTION, "inbound");

        assertEquals(InitialCallDirectionResolver.Decision.INBOUND,
                InitialCallDirectionResolver.resolve(headers, true, true));
    }

    @Test
    void knownInternalAgentWithoutInboundRouteIsOutbound() {
        Map<String, String> headers = Map.of(
                EslEventUtil.VARIABLE_SOFIA_PROFILE_NAME, "internal",
                EslEventUtil.VARIABLE_SIP_USER_AGENT, "JsSIP 3.13.8",
                EslEventUtil.CALLER_DIRECTION, "inbound");

        assertEquals(InitialCallDirectionResolver.Decision.OUTBOUND,
                InitialCallDirectionResolver.resolve(headers, false, true));
    }

    @Test
    void correlatedLegDoesNotCreateAnotherInitialCall() {
        Map<String, String> headers = Map.of(
                EslEventUtil.VARIABLE_OCH_CALL_ID, "123456",
                EslEventUtil.VARIABLE_SOFIA_PROFILE_NAME, "external",
                EslEventUtil.CALLER_DIRECTION, "inbound");

        assertEquals(InitialCallDirectionResolver.Decision.CORRELATED,
                InitialCallDirectionResolver.resolve(headers, true, false));
    }

    @Test
    void unknownCallerWithOutboundChannelDirectionDefaultsToInbound() {
        Map<String, String> headers = Map.of(
                EslEventUtil.VARIABLE_SOFIA_PROFILE_NAME, "internal",
                EslEventUtil.CALLER_DIRECTION, "outbound",
                EslEventUtil.VARIABLE_SIP_USER_AGENT, "Gateway");

        assertEquals(InitialCallDirectionResolver.Decision.INBOUND,
                InitialCallDirectionResolver.resolve(headers, false, false));
    }

    @Test
    void unknownCallerWithoutDirectionDefaultsToInbound() {
        Map<String, String> headers = Map.of(
                EslEventUtil.VARIABLE_SOFIA_PROFILE_NAME, "internal",
                EslEventUtil.VARIABLE_SIP_USER_AGENT, "Gateway");

        assertEquals(InitialCallDirectionResolver.Decision.INBOUND,
                InitialCallDirectionResolver.resolve(headers, false, false));
    }

    @Test
    void externalInboundEvidenceIsAvailableAsFinalOutboundGuard() {
        Map<String, String> headers = Map.of(
                EslEventUtil.VARIABLE_SOFIA_PROFILE_URL, "sofia/external/127.0.0.1",
                EslEventUtil.CALLER_CONTEXT, "public");

        assertEquals(true, InitialCallDirectionResolver.hasExternalInboundEvidence(headers));
    }
}
