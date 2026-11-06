package com.och.security.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * freeswitch xml_curl接口校验
 *
 * @author danmo
 */
@Slf4j
@Component
public class FsXmlCurlInterceptor implements HandlerInterceptor {

    @Value("${fs.xml-curl.auth-scheme:basic}")
    private String authScheme;

    @Value("${fs.xml-curl.secretKey:}")
    private String secretKey;


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (isLoopbackRequest(request)) {
            return true;
        }
        // auth-scheme: digest
        // @see com.atomscat.freeswitch.xml.config.SecurityConfiguration
        if ("digest".equals(authScheme)) {
            return true;
        }

        // auth-scheme: basic, you need delete pom.xml security dependency
        if ("basic".equals(authScheme)) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.regionMatches(true, 0, "Basic ", 0, 6)) {
                unauthorized(response);
                return false;
            }
            String resp;
            try {
                resp = base64Decode(authHeader.substring(6));
            } catch (IllegalArgumentException e) {
                log.warn("Rejected malformed XML-Curl basic authentication from remote={}", request.getRemoteAddr());
                unauthorized(response);
                return false;
            }

            if (StringUtils.isEmpty(resp)) {
                unauthorized(response);
                return false;
            }

            boolean authenticated = authIdentity(resp);
            log.debug("XML-Curl basic authentication {} from remote={}",
                    authenticated ? "accepted" : "rejected", request.getRemoteAddr());
            if (!authenticated) {
                unauthorized(response);
            }
            return authenticated;
        }
        unauthorized(response);
        return false;
    }

    private void unauthorized(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        try {
            response.getWriter().write("Unauthorized XML-Curl request");
        } catch (IOException e) {
            log.warn("Failed to write XML-Curl unauthorized response", e);
        }
    }

    private boolean isLoopbackRequest(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        String remoteHost = request.getRemoteHost();
        return "127.0.0.1".equals(remoteAddr)
                || "0:0:0:0:0:0:0:1".equals(remoteAddr)
                || "::1".equals(remoteAddr)
                || "localhost".equalsIgnoreCase(remoteHost);
    }

    public static String base64Decode(String str) {
        if (StringUtils.isBlank(str)) {
            return null;
        }
        byte[] base64decodedBytes = Base64.getDecoder().decode(str);
        return new String(base64decodedBytes, StandardCharsets.UTF_8);
    }


    private Boolean authIdentity(String resp) {
        if (StringUtils.isBlank(secretKey) || StringUtils.isBlank(resp)) {
            return false;
        }
        int separator = resp.indexOf(':');
        String suppliedSecret = separator >= 0 ? resp.substring(separator + 1) : resp;
        return MessageDigest.isEqual(secretKey.getBytes(StandardCharsets.UTF_8),
                suppliedSecret.getBytes(StandardCharsets.UTF_8));
    }
}
