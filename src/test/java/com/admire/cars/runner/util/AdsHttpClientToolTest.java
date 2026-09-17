package com.admire.cars.runner.util;

import com.admire.cars.runner.constant.Constant;
import com.admire.cars.runner.constant.StatusConstant;
import com.admire.cars.runner.dto.IpVerificationDto;
import com.admire.cars.runner.entity.AdsMatrixAffiliateInfo;
import com.admire.cars.runner.entity.AdsMatrixInfo;
import com.admire.cars.runner.entity.AdsNormalInfo;
import com.admire.cars.runner.repository.AdsTaskLogRepository;
import com.admire.cars.runner.repository.IpProxyInfoRepository;
import com.admire.cars.runner.service.proxy.IpProxyService;
import com.admire.cars.runner.service.proxy.UserAgentService;
import okhttp3.OkHttpClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class AdsHttpClientToolTest {

    @Mock
    private IpProxyService ipProxyService;

    @Mock
    private UserAgentService userAgentService;

    @Mock
    private AdsTaskLogRepository adsTaskLogRepository;

    @Mock
    private IpProxyInfoRepository ipProxyInfoRepository;

    @InjectMocks
    private AdsHttpClientTool adsHttpClientTool;

    @Test
    void applyAffiliateAd_normalFollowRedirectsToFinalUrl() throws Exception {
        HttpServer server = startRedirectServer();
        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            AdsNormalInfo adsNormalInfo = buildNormalInfo(baseUrl);
            mockCommonDependencies();

            AdsHttpResponseDto response = adsHttpClientTool.applyAffiliateAd(adsNormalInfo);

            assertEquals(StatusConstant.SUCCESS, response.getStatus());
            assertEquals(baseUrl + "/final?lkid=82853225&subid=566&cid=final", response.getUrl());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void applyAffiliateAd_normalFollowsServerRedirectToFinalUrl() throws Exception {
        HttpServer server = startServerRedirectServer();
        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            AdsNormalInfo adsNormalInfo = buildNormalInfo(baseUrl);
            mockCommonDependencies();

            AdsHttpResponseDto response = adsHttpClientTool.applyAffiliateAd(adsNormalInfo);

            assertEquals(StatusConstant.SUCCESS, response.getStatus());
            assertEquals(baseUrl + "/final?lkid=82853225&subid=566&cid=final", response.getUrl());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void applyAffiliateAd_normalFollowsPermanentServerRedirectsToFinalUrl() throws Exception {
        HttpServer server = startPermanentServerRedirectServer();
        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            AdsNormalInfo adsNormalInfo = buildNormalInfo(baseUrl);
            mockCommonDependencies();

            AdsHttpResponseDto response = adsHttpClientTool.applyAffiliateAd(adsNormalInfo);

            assertEquals(StatusConstant.SUCCESS, response.getStatus());
            assertEquals(baseUrl + "/final?irclickid=WscS7Y1mLxyZRryzd41Uey8nUkrzycyShzTt2M0&irgwc=1&afsrc=1&utm_source=impact&utm_medium=affiliate&utm_campaign=5498623&sharedid=", response.getUrl());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void applyAffiliateAd_normalFollowRefreshHeaderRedirectsToFinalUrl() throws Exception {
        HttpServer server = startRefreshRedirectServer();
        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            AdsNormalInfo adsNormalInfo = buildNormalInfo(baseUrl);
            mockCommonDependencies();

            AdsHttpResponseDto response = adsHttpClientTool.applyAffiliateAd(adsNormalInfo);

            assertEquals(StatusConstant.SUCCESS, response.getStatus());
            assertEquals(baseUrl + "/final?lkid=82853225&subid=566&cid=final", response.getUrl());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void applyAffiliateAd_matrixFollowRedirectsToFinalUrl() throws Exception {
        HttpServer server = startRedirectServer();
        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            AdsMatrixInfo matrixInfo = buildMatrixInfo(baseUrl);
            AdsMatrixAffiliateInfo affiliateInfo = buildMatrixAffiliateInfo(baseUrl);
            mockCommonDependencies();

            AdsHttpResponseDto response = adsHttpClientTool.applyAffiliateAd(matrixInfo, affiliateInfo);

            assertEquals(StatusConstant.SUCCESS, response.getStatus());
            assertEquals(baseUrl + "/final?lkid=82853225&subid=566&cid=final", response.getUrl());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void applyAffiliateAd_matrixHandlesAbsoluteRedirectWithIllegalQueryCharacters() throws Exception {
        HttpServer server = startAbsoluteLocationRedirectServer();
        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            AdsMatrixInfo matrixInfo = buildMatrixInfo(baseUrl);
            AdsMatrixAffiliateInfo affiliateInfo = buildMatrixAffiliateInfo(baseUrl);
            mockCommonDependencies();

            AdsHttpResponseDto response = adsHttpClientTool.applyAffiliateAd(matrixInfo, affiliateInfo);

            assertEquals(StatusConstant.SUCCESS, response.getStatus());
            assertEquals(baseUrl + "/final?c=InterContinental%7C%20Best%20Price%20Guarantee", response.getUrl());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void applyAffiliateAd_matrixHandlesAbsoluteRedirectWithPercentInQuery() throws Exception {
        HttpServer server = startPercentRedirectServer();
        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            AdsMatrixInfo matrixInfo = buildMatrixInfo(baseUrl);
            AdsMatrixAffiliateInfo affiliateInfo = buildMatrixAffiliateInfo(baseUrl);
            mockCommonDependencies();

            AdsHttpResponseDto response = adsHttpClientTool.applyAffiliateAd(matrixInfo, affiliateInfo);

            assertEquals(StatusConstant.SUCCESS, response.getStatus());
            assertEquals(baseUrl + "/final?c=IHG%20Summer%20Sale.%20Save%20Up%20to%2030%25%20By%207/30", response.getUrl());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void applyAffiliateAd_matrixHandlesAffiliateUrlWithBracketCharacter() throws Exception {
        HttpServer server = startRedirectServer();
        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            AdsMatrixInfo matrixInfo = buildMatrixInfo(baseUrl);
            AdsMatrixAffiliateInfo affiliateInfo = buildMatrixAffiliateInfo(baseUrl);
            affiliateInfo.setAffiliteUrl(baseUrl + "/index/index/openurl?camref=1011lkzHo&pubref=151966291x21515177451/[loyalty:151966291");
            mockCommonDependencies();

            AdsHttpResponseDto response = adsHttpClientTool.applyAffiliateAd(matrixInfo, affiliateInfo);

            assertEquals(StatusConstant.SUCCESS, response.getStatus());
            assertEquals(baseUrl + "/final?lkid=82853225&subid=566&cid=final", response.getUrl());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void applyAffiliateAd_matrixPreservesClientRedirectSuffix() throws Exception {
        HttpServer server = startClientRedirectSuffixServer();
        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            AdsMatrixInfo matrixInfo = buildMatrixInfo(baseUrl);
            AdsMatrixAffiliateInfo affiliateInfo = buildMatrixAffiliateInfo(baseUrl);
            affiliateInfo.setAffiliteUrl(baseUrl + "/index/index/openurl?track=43615712065c6043&url=");
            mockCommonDependencies();

            AdsHttpResponseDto response = adsHttpClientTool.applyAffiliateAd(matrixInfo, affiliateInfo);

            assertEquals(StatusConstant.SUCCESS, response.getStatus());
            assertEquals(
                    baseUrl + "/final?affiliate_id=133734&click_id=5601845655&clickId=5601845655&utm_source=pepperjam&utm_medium=affiliate&utm_campaign=133734",
                    response.getUrl());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void applyAffiliateAd_normalFollowsSearchParamBasedJsRedirects() throws Exception {
        HttpServer server = startSearchParamRedirectServer();
        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            AdsNormalInfo adsNormalInfo = new AdsNormalInfo();
            adsNormalInfo.setAffiliteUrl(baseUrl + "/index/index/openurl?store_url="
                    + URLEncoder.encode(baseUrl + "/step2", StandardCharsets.UTF_8)
                    + "&c=3663");
            adsNormalInfo.setLandingPageUrl(baseUrl + "/final");
            adsNormalInfo.setCampainCountry("US");
            adsNormalInfo.setDynamicProxyInfo(null);
            adsNormalInfo.setAdsOwner("13800000000");
            adsNormalInfo.setCampainName("Normal Campaign");
            adsNormalInfo.setPlatformName("Platform");
            adsNormalInfo.setStatus("RUNNING");
            mockCommonDependencies();

            AdsHttpResponseDto response = adsHttpClientTool.applyAffiliateAd(adsNormalInfo);

            assertEquals(StatusConstant.SUCCESS, response.getStatus());
            assertEquals(baseUrl + "/final", response.getUrl());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void applyAffiliateAd_normalPreservesClientRedirectSuffix() throws Exception {
        HttpServer server = startClientRedirectSuffixServer();
        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            AdsNormalInfo adsNormalInfo = new AdsNormalInfo();
            adsNormalInfo.setAffiliteUrl(baseUrl + "/index/index/openurl?track=43615712065c6043&url=");
            adsNormalInfo.setLandingPageUrl(baseUrl + "/final");
            adsNormalInfo.setCampainCountry("US");
            adsNormalInfo.setDynamicProxyInfo(null);
            adsNormalInfo.setAdsOwner("13800000000");
            adsNormalInfo.setCampainName("Normal Campaign");
            adsNormalInfo.setPlatformName("Platform");
            adsNormalInfo.setStatus("RUNNING");
            mockCommonDependencies();

            AdsHttpResponseDto response = adsHttpClientTool.applyAffiliateAd(adsNormalInfo);

            assertEquals(StatusConstant.SUCCESS, response.getStatus());
            assertEquals(
                    baseUrl + "/final?affiliate_id=133734&click_id=5601845655&clickId=5601845655&utm_source=pepperjam&utm_medium=affiliate&utm_campaign=133734",
                    response.getUrl());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void applyAffiliateAd_normalFollowsVarAssignedJavascriptRedirect() throws Exception {
        HttpServer server = startVarAssignedJsRedirectServer();
        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            AdsNormalInfo adsNormalInfo = new AdsNormalInfo();
            adsNormalInfo.setAffiliteUrl(baseUrl + "/index/index/openurl?track=43615712065c6043&url=");
            adsNormalInfo.setLandingPageUrl(baseUrl + "/final");
            adsNormalInfo.setCampainCountry("US");
            adsNormalInfo.setDynamicProxyInfo(null);
            adsNormalInfo.setAdsOwner("13800000000");
            adsNormalInfo.setCampainName("Normal Campaign");
            adsNormalInfo.setPlatformName("Platform");
            adsNormalInfo.setStatus("RUNNING");
            mockCommonDependencies();

            AdsHttpResponseDto response = adsHttpClientTool.applyAffiliateAd(adsNormalInfo);

            assertEquals(StatusConstant.SUCCESS, response.getStatus());
            assertEquals(
                    baseUrl + "/final?affiliate_id=133734&click_id=5601876235&clickId=5601876235&utm_source=pepperjam&utm_medium=affiliate&utm_campaign=133734",
                    response.getUrl());
        } finally {
            server.stop(0);
        }
    }

    private void mockCommonDependencies() {
        doReturn("Mozilla/5.0").when(userAgentService).getUserAgent();
        doReturn(new OkHttpClient()).when(ipProxyService).buildOkHttpClient(any());
        doReturn(new IpVerificationDto("127.0.0.1", "US", true))
                .when(ipProxyService)
                .ipVerification4OkHttpClient(any(), eq("US"));
    }

    private AdsNormalInfo buildNormalInfo(String baseUrl) {
        AdsNormalInfo adsNormalInfo = new AdsNormalInfo();
        adsNormalInfo.setAffiliteUrl(baseUrl + "/index/index/openurl?track=8f3cc583cb6b3419&url=");
        adsNormalInfo.setLandingPageUrl(baseUrl + "/final");
        adsNormalInfo.setCampainCountry("US");
        adsNormalInfo.setDynamicProxyInfo(null);
        adsNormalInfo.setAdsOwner("13800000000");
        adsNormalInfo.setCampainName("Normal Campaign");
        adsNormalInfo.setPlatformName("Platform");
        adsNormalInfo.setStatus("RUNNING");
        return adsNormalInfo;
    }

    private AdsMatrixInfo buildMatrixInfo(String baseUrl) {
        AdsMatrixInfo matrixInfo = new AdsMatrixInfo();
        matrixInfo.setLandingPageUrl(baseUrl + "/final");
        matrixInfo.setCampainCountry("US");
        matrixInfo.setDynamicProxyInfo(null);
        matrixInfo.setAdsOwner("13800000000");
        matrixInfo.setCampainName("Matrix Campaign");
        matrixInfo.setStatus("RUNNING");
        return matrixInfo;
    }

    private AdsMatrixAffiliateInfo buildMatrixAffiliateInfo(String baseUrl) {
        AdsMatrixAffiliateInfo affiliateInfo = new AdsMatrixAffiliateInfo();
        affiliateInfo.setAffiliteUrl(baseUrl + "/index/index/openurl?track=8f3cc583cb6b3419&url=");
        affiliateInfo.setPlatformName("Platform");
        affiliateInfo.setRemarks("remark");
        return affiliateInfo;
    }

    private HttpServer startRedirectServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/index/index/openurl", exchange -> {
            String host = exchange.getRequestHeaders().getFirst("Host");
            String body = "<html><head><script>window.location.href='http://" + host + "/step2';</script></head><body>redirecting</body></html>";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.createContext("/step2", exchange -> {
            exchange.getResponseHeaders().add("Location", "/final?lkid=82853225&subid=566&cid=final");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/final", exchange -> {
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    private HttpServer startServerRedirectServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/index/index/openurl", exchange -> {
            exchange.getResponseHeaders().add("Location", "/step2");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/step2", exchange -> {
            exchange.getResponseHeaders().add("Location", "/final?lkid=82853225&subid=566&cid=final");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/final", exchange -> {
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    private HttpServer startPermanentServerRedirectServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/index/index/openurl", exchange -> {
            exchange.getResponseHeaders().add("Location", "/step1?sharedId=&subId1=T2oFu0000m1gk464wg&subId3=");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/step1", exchange -> {
            exchange.getResponseHeaders().add("Location", "/step2?irclickid=WscS7Y1mLxyZRryzd41Uey8nUkrzycyShzTt2M0&irgwc=1&afsrc=1&utm_source=impact&utm_medium=affiliate&utm_campaign=5498623&sharedid=");
            exchange.sendResponseHeaders(301, -1);
            exchange.close();
        });
        server.createContext("/step2", exchange -> {
            exchange.getResponseHeaders().add("Location", "/step3?irclickid=WscS7Y1mLxyZRryzd41Uey8nUkrzycyShzTt2M0&irgwc=1&afsrc=1&utm_source=impact&utm_medium=affiliate&utm_campaign=5498623&sharedid=");
            exchange.sendResponseHeaders(308, -1);
            exchange.close();
        });
        server.createContext("/step3", exchange -> {
            exchange.getResponseHeaders().add("Location", "/final?irclickid=WscS7Y1mLxyZRryzd41Uey8nUkrzycyShzTt2M0&irgwc=1&afsrc=1&utm_source=impact&utm_medium=affiliate&utm_campaign=5498623&sharedid=");
            exchange.sendResponseHeaders(301, -1);
            exchange.close();
        });
        server.createContext("/final", exchange -> {
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    private HttpServer startRefreshRedirectServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/index/index/openurl", exchange -> {
            exchange.getResponseHeaders().add("Refresh", "0; url=/step2");
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.createContext("/step2", exchange -> {
            exchange.getResponseHeaders().add("Location", "/final?lkid=82853225&subid=566&cid=final");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/final", exchange -> {
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    private HttpServer startAbsoluteLocationRedirectServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/index/index/openurl", exchange -> {
            String host = exchange.getRequestHeaders().getFirst("Host");
            exchange.getResponseHeaders().add("Location",
                    "http://" + host + "/final?c=InterContinental| Best Price Guarantee");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/final", exchange -> {
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    private HttpServer startPercentRedirectServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/index/index/openurl", exchange -> {
            String host = exchange.getRequestHeaders().getFirst("Host");
            exchange.getResponseHeaders().add("Location",
                    "http://" + host + "/final?c=IHG Summer Sale. Save Up to 30% By 7/30");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/final", exchange -> {
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    private HttpServer startSearchParamRedirectServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/index/index/openurl", exchange -> {
            String body = "<html><head><script>"
                    + "const url = new URL(location.href);"
                    + "const trackingLink = url.searchParams.get('store_url');"
                    + "if (trackingLink) { window.location.assign(trackingLink); }"
                    + "</script></head><body>redirecting</body></html>";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.createContext("/step2", exchange -> {
            exchange.getResponseHeaders().add("Location", "/final");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/final", exchange -> {
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    private HttpServer startClientRedirectSuffixServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/index/index/openurl", exchange -> {
            String host = exchange.getRequestHeaders().getFirst("Host");
            String body = "<html><head><script>window.location.href='http://" + host + "/bridge?url="
                    + URLEncoder.encode("http://" + host + "/final", StandardCharsets.UTF_8)
                    + "&sid=lh_5xu07l7jk6ae';</script></head><body>redirecting</body></html>";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.createContext("/bridge", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            if (query == null || !query.contains("sid=lh_5xu07l7jk6ae")) {
                byte[] body = "missing sid".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(400, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
                return;
            }
            exchange.getResponseHeaders().add("Location", "/final?affiliate_id=133734&click_id=5601845655&clickId=5601845655&utm_source=pepperjam&utm_medium=affiliate&utm_campaign=133734");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/final", exchange -> {
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    private HttpServer startVarAssignedJsRedirectServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/index/index/openurl", exchange -> {
            String host = exchange.getRequestHeaders().getFirst("Host");
            String body = "<!DOCTYPE html><html><head><script type=\"text/javascript\">"
                    + "var u = 'http://" + host + "/bridge?url=http://www.michaelstars.com&sid=lh_5x7uptulmrz6';"
                    + "location.replace(u);"
                    + "</script></head><body></body></html>";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.createContext("/bridge", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            if (query == null || !query.contains("sid=lh_5x7uptulmrz6")) {
                byte[] body = "missing sid".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(400, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
                return;
            }
            exchange.getResponseHeaders().add("Location", "/final?affiliate_id=133734&click_id=5601876235&clickId=5601876235&utm_source=pepperjam&utm_medium=affiliate&utm_campaign=133734");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/final", exchange -> {
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }
}
