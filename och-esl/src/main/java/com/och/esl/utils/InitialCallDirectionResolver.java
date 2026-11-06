package com.och.esl.utils;

import java.util.Locale;
import java.util.Map;

/**
 * Resolves the direction of the first channel before an OCH call has been
 * stored in Redis.
 *
 * <p>FreeSWITCH reports a SIP phone's incoming INVITE as an inbound channel
 * even when the phone is dialing out.  The channel direction alone therefore
 * cannot distinguish an agent originated call from an external gateway call.
 * Transport profile, public context, an active inbound route and the known
 * agent number are used in that order instead.</p>
 */
public final class InitialCallDirectionResolver {

    private static final String INBOUND = "inbound";
    private static final String OUTBOUND = "outbound";

    private InitialCallDirectionResolver() {
    }

    public enum Decision {
        INBOUND,
        OUTBOUND,
        CORRELATED,
        UNKNOWN
    }

    public static Decision resolve(Map<String, String> headers,
                                   boolean inboundRouteMatched,
                                   boolean knownAgent) {
        if (headers == null || headers.isEmpty()) {
            return Decision.UNKNOWN;
        }
        if (hasCorrelation(headers)) {
            return Decision.CORRELATED;
        }

        String profileName = firstNonBlank(
                headers.get(EslEventUtil.VARIABLE_SOFIA_PROFILE_NAME),
                headers.get("variable_recovery_profile_name"),
                headers.get(EslEventUtil.VARIABLE_SOFIA_PROFILE_URL),
                headers.get("Channel-Name"));
        String context = firstNonBlank(headers.get(EslEventUtil.CALLER_CONTEXT),
                headers.get("variable_user_context"));

        if (isExternalProfile(profileName) || equalsIgnoreCase(context, "public")) {
            return Decision.INBOUND;
        }
        if (inboundRouteMatched) {
            return Decision.INBOUND;
        }
        if (knownAgent) {
            return Decision.OUTBOUND;
        }

        String explicitDirection = firstNonBlank(
                headers.get("Call-Direction"),
                headers.get(EslEventUtil.CALLER_LOGICAL_DIRECTION),
                headers.get(EslEventUtil.CALLER_DIRECTION),
                headers.get("Presence-Call-Direction"),
                headers.get("variable_direction"));
        if (equalsIgnoreCase(explicitDirection, OUTBOUND)) {
            return isInternalProfile(profileName) && isSoftphone(headers.get(EslEventUtil.VARIABLE_SIP_USER_AGENT))
                    ? Decision.OUTBOUND : Decision.INBOUND;
        }
        if (isInternalProfile(profileName) && isSoftphone(headers.get(EslEventUtil.VARIABLE_SIP_USER_AGENT))) {
            return Decision.OUTBOUND;
        }
        if (equalsIgnoreCase(explicitDirection, INBOUND)) {
            return Decision.INBOUND;
        }
        return Decision.INBOUND;
    }

    public static boolean hasExternalInboundEvidence(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return false;
        }
        String profileName = firstNonBlank(
                headers.get(EslEventUtil.VARIABLE_SOFIA_PROFILE_NAME),
                headers.get("variable_recovery_profile_name"),
                headers.get(EslEventUtil.VARIABLE_SOFIA_PROFILE_URL),
                headers.get("Channel-Name"));
        String context = firstNonBlank(headers.get(EslEventUtil.CALLER_CONTEXT),
                headers.get("variable_user_context"));
        return isExternalProfile(profileName) || equalsIgnoreCase(context, "public");
    }

    private static boolean hasCorrelation(Map<String, String> headers) {
        return isNotBlank(headers.get(EslEventUtil.VARIABLE_OCH_CALL_ID))
                || isNotBlank(headers.get(EslEventUtil.VARIABLE_SIP_OCH_CALL_ID))
                || isNotBlank(headers.get(EslEventUtil.VARIABLE_ORIGINATION_UUID))
                || isNotBlank(headers.get("Origination-UUID"))
                || isNotBlank(headers.get("variable_originate_endpoint"));
    }

    private static boolean isExternalProfile(String profileName) {
        String normalized = normalize(profileName);
        return "external".equals(normalized)
                || "public".equals(normalized)
                || normalized.contains("/external/")
                || normalized.endsWith("/external")
                || normalized.contains("@external");
    }

    private static boolean isInternalProfile(String profileName) {
        String normalized = normalize(profileName);
        return "internal".equals(normalized)
                || normalized.contains("/internal/")
                || normalized.endsWith("/internal")
                || normalized.contains("@internal");
    }

    private static boolean isSoftphone(String userAgent) {
        String normalized = normalize(userAgent);
        return normalized.contains("jssip")
                || normalized.contains("microsip")
                || normalized.contains("linphone")
                || normalized.contains("pjsip");
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.trim().equalsIgnoreCase(right);
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
