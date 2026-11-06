package com.och.security.interceptor;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FsXmlCurlInterceptorTest {

    @Test
    void accepts_only_the_exact_basic_auth_secret() {
        FsXmlCurlInterceptor interceptor = interceptorWithSecret("expected-secret");

        assertTrue(interceptor.preHandle(requestWithBasicCredential("freeswitch:expected-secret"),
                new MockHttpServletResponse(), new Object()));

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(requestWithBasicCredential("freeswitch:prefix-expected-secret"),
                response, new Object()));
        org.junit.jupiter.api.Assertions.assertEquals(401, response.getStatus());
    }

    @Test
    void rejects_malformed_or_blank_basic_auth_configuration() {
        FsXmlCurlInterceptor interceptor = interceptorWithSecret("expected-secret");
        MockHttpServletRequest malformed = remoteRequest();
        malformed.addHeader("Authorization", "Basic not-valid-base64");
        assertFalse(interceptor.preHandle(malformed, new MockHttpServletResponse(), new Object()));

        FsXmlCurlInterceptor withoutSecret = interceptorWithSecret("");
        assertFalse(withoutSecret.preHandle(requestWithBasicCredential("freeswitch:any-value"),
                new MockHttpServletResponse(), new Object()));
    }

    private FsXmlCurlInterceptor interceptorWithSecret(String secret) {
        FsXmlCurlInterceptor interceptor = new FsXmlCurlInterceptor();
        ReflectionTestUtils.setField(interceptor, "authScheme", "basic");
        ReflectionTestUtils.setField(interceptor, "secretKey", secret);
        return interceptor;
    }

    private MockHttpServletRequest requestWithBasicCredential(String credential) {
        MockHttpServletRequest request = remoteRequest();
        request.addHeader("Authorization", "Basic " + Base64.getEncoder()
                .encodeToString(credential.getBytes(StandardCharsets.UTF_8)));
        return request;
    }

    private MockHttpServletRequest remoteRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.10");
        request.setRemoteHost("example.invalid");
        return request;
    }
}
