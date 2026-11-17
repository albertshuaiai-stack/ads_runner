package com.admire.cars.runner.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthInterceptorTest {

    @Mock
    private JwtTokenService tokenService;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private Object handler;

    private AuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new AuthInterceptor(tokenService);
    }

    @Test
    void platformPostbackWithoutToken_isAllowed() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/postback");

        assertTrue(interceptor.preHandle(request, response, handler));
        verifyNoInteractions(response);
    }

    @Test
    void platformPostbackVariantsWithoutToken_areAllowed() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/pb/postback");
        assertTrue(interceptor.preHandle(request, response, handler));

        when(request.getRequestURI()).thenReturn("/api/yp/postback");
        assertTrue(interceptor.preHandle(request, response, handler));
        verifyNoInteractions(response);
    }

    @Test
    void adminGetPostbackWithoutToken_isRejected() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

        assertFalse(interceptor.preHandle(request, response, handler));
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void importCsvWithoutToken_isRejected() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/postback/import");
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

        assertFalse(interceptor.preHandle(request, response, handler));
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void adminGetPostbackWithValidToken_passesAndSetsUserId() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("AMtoken")).thenReturn("valid-token");
        when(tokenService.validateToken("valid-token")).thenReturn(42L);

        assertTrue(interceptor.preHandle(request, response, handler));
        verify(request).setAttribute("userId", 42L);
        verifyNoInteractions(response);
    }
}
