package com.och.security.handler;

import com.och.common.base.ResResult;
import com.och.common.constant.HttpStatus;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    @Test
    void methodMismatchUsesBadMethodApplicationCode() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/tts/v1/runtime");

        ResResult<?> result = new GlobalExceptionHandler()
                .handleHttpRequestMethodNotSupported(
                        new HttpRequestMethodNotSupportedException("POST"), request);

        assertEquals(HttpStatus.BAD_METHOD, result.getCode());
    }
}
