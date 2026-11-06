package com.och.security.filter;

import com.alibaba.fastjson.JSONObject;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.och.common.config.redis.RedisService;
import com.och.common.constant.CacheConstants;
import com.och.common.constant.SecurityConstants;
import com.och.common.constant.TokenConstants;
import com.och.security.authority.LoginUserInfo;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void refreshesLoginSessionInMinutesInsteadOfSeconds() throws Exception {
        long userId = 7L;
        LoginUserInfo userInfo = new LoginUserInfo();
        userInfo.setUserId(userId);
        userInfo.setUsername("admin");
        userInfo.setAuthorities(Collections.emptySet());

        String token = JWT.create()
                .withClaim(SecurityConstants.USER_ID, userId)
                .withClaim(SecurityConstants.USER_NAME, userInfo.getUsername())
                .withClaim(SecurityConstants.LOGIN_USER, JSONObject.toJSONString(userInfo))
                .sign(Algorithm.HMAC512(TokenConstants.SECRET));
        String cacheKey = CacheConstants.LOGIN_TOKEN_KEY + userId;

        RedisService redisService = mock(RedisService.class);
        when(redisService.getCacheObject(cacheKey)).thenReturn(token);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TokenConstants.AUTHENTICATION, TokenConstants.PREFIX + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        new JwtAuthFilter(redisService).doFilter(request, response, filterChain);

        verify(redisService).expire(cacheKey, CacheConstants.EXPIRATION, TimeUnit.MINUTES);
        verify(filterChain).doFilter(request, response);
    }
}
