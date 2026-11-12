package com.admire.cars.runner.controller;

import com.admire.cars.runner.entity.AdsNormalPostBack;
import com.admire.cars.runner.service.AdsApiConsumeService;
import com.admire.cars.runner.service.AdsNormalPostBackService;
import com.admire.cars.runner.service.PostBackAttributionService;
import com.admire.cars.runner.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdsApiConsumeControllerTest {

    @Mock
    private UserService userService;
    @Mock
    private AdsApiConsumeService adsApiConsumeService;
    @Mock
    private AdsNormalPostBackService adsNormalPostBackService;
    @Mock
    private PostBackAttributionService postBackAttributionService;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private HttpServletRequest request;

    private static final String CLICK_ID = "11111111-2222-3333-4444-555555555555";

    @Test
    void postback_acceptsWebIdAliasFromGlobalPostbackPage() {
        AdsApiConsumeController controller = new AdsApiConsumeController(userService,
                adsApiConsumeService, adsNormalPostBackService, postBackAttributionService, objectMapper);
        when(request.getParameter(anyString())).thenReturn(null);
        when(request.getParameter("web_id")).thenReturn(CLICK_ID);

        ResponseEntity<Map<String, Object>> response = controller.createByJson(
                "api-key", "shop-1", "Shop One", "sign-1", "order-1",
                "2026-11-12 10:00:00", "100.00", "12.50", "Approved",
                null, null, "2026-11-12 09:55:00", request);

        ArgumentCaptor<AdsNormalPostBack> captor = ArgumentCaptor.forClass(AdsNormalPostBack.class);
        verify(adsNormalPostBackService).create(captor.capture(), eq("api-key"));
        assertEquals(CLICK_ID, captor.getValue().getSubId());
        assertEquals("order-1", captor.getValue().getOrderNo());
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(postBackAttributionService).attribute(captor.getValue().getAdsOwner(), "order-1");
    }

    @Test
    void postback_prefersExplicitSubIdOverAlias() {
        AdsApiConsumeController controller = new AdsApiConsumeController(userService,
                adsApiConsumeService, adsNormalPostBackService, postBackAttributionService, objectMapper);

        ResponseEntity<Map<String, Object>> response = controller.createByJson(
                "api-key", null, null, null, "order-2", null, null, null, null,
                CLICK_ID, null, null, request);

        ArgumentCaptor<AdsNormalPostBack> captor = ArgumentCaptor.forClass(AdsNormalPostBack.class);
        verify(adsNormalPostBackService).create(captor.capture(), eq("api-key"));
        assertEquals(CLICK_ID, captor.getValue().getSubId());
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }
}
