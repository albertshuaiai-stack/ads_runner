package com.admire.cars.runner.service.proxy;

import com.admire.cars.runner.config.AutoTaskConfig;
import com.admire.cars.runner.constant.Constant;
import com.admire.cars.runner.dto.IpVerificationDto;
import com.admire.cars.runner.entity.IpProxyInfo;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class IpProxyService {

    private static final Logger log = LoggerFactory.getLogger(IpProxyService.class);

    @Autowired
    private AutoTaskConfig adsConfig;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AutoTaskConfig autoTaskConfig;


    /**
     * ipVerification4OkHttpClient
     * @param httpClient
     * @param expectedCountryCode
     * @return
     * @throws IOException
     */
    public IpVerificationDto ipVerification4OkHttpClient(
            OkHttpClient httpClient,
            String expectedCountryCode) throws IOException {
        if (!autoTaskConfig.isIpVerification()) {
            return new IpVerificationDto("",expectedCountryCode,true);
        }

        IpEndpoint[] endpoints = {
                new IpEndpoint("https://api.country.is/", new String[]{"ip"}, new String[]{"country"}),
                new IpEndpoint("https://ipapi.co/json/", new String[]{"ip"}, new String[]{"country_code"}),
                new IpEndpoint("https://httpbin.org/ip", new String[]{"origin"}, new String[]{}),
        };

        // Also try configured URL if provided
        String configuredUrl = null;
        if (StringUtils.hasText(adsConfig.getIpLookupUrl())) {
            configuredUrl = adsConfig.getIpLookupUrl().trim();
            validateHttpUrl(configuredUrl, "IP lookup url");
        }

        IOException lastException = null;

        // Try configured URL first if available
        if (StringUtils.hasText(configuredUrl)) {
            try {
                IpVerificationDto result = this.attemptIpLookup4OkHttpClient(httpClient, configuredUrl,
                        new String[]{"ip", "query"}, new String[]{"country", "countryCode", "country_code"});
                if (result != null) {
                    log.info("IP Verification succeeded with configured URL: {}", configuredUrl);
                    result.setMatched(result.getCountryCode() != null &&
                            result.getCountryCode().equalsIgnoreCase(expectedCountryCode));
                    return result;
                }
            } catch (IOException e) {
                log.warn("Configured IP lookup URL failed: {} - {}", configuredUrl, e.getMessage());
                lastException = e;
            }
        }

        // Try each predefined endpoint
        for (IpEndpoint endpoint : endpoints) {
            try {
                IpVerificationDto result = attemptIpLookup4OkHttpClient(httpClient, endpoint.url,
                        endpoint.ipFields, endpoint.countryFields);
                if (result != null) {
                    log.info("IP Verification succeeded with endpoint: {} (IP: {}, Country: {})",
                            endpoint.url, result.getIp(), result.getCountryCode());
                    result.setMatched(result.getCountryCode() != null &&
                            result.getCountryCode().equalsIgnoreCase(expectedCountryCode));
                    return result;
                }
            } catch (IOException e) {
                log.debug("IP lookup endpoint {} failed: {}", endpoint.url, e.getMessage());
                lastException = e;
            }
        }

        // All endpoints failed
        if (lastException != null) {
            throw new IOException("All IP lookup endpoints failed. Last error: " + lastException.getMessage(), lastException);
        }
        throw new IOException("No valid IP lookup endpoint returned data");
    }

    /**
     * ipVerification4OkHttpClient
     * @param httpClient
     * @param expectedCountryCode
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    public IpVerificationDto  ipVerification4HttpClient(
            HttpClient httpClient,
            String expectedCountryCode) throws IOException, InterruptedException {
        if (!autoTaskConfig.isIpVerification()) {
            return new IpVerificationDto("",expectedCountryCode,true);
        }

        IpEndpoint[] endpoints = {
                new IpEndpoint("https://api.country.is/", new String[]{"ip"}, new String[]{"country"}),
                new IpEndpoint("https://ipapi.co/json/", new String[]{"ip"}, new String[]{"country_code"}),
                new IpEndpoint("https://httpbin.org/ip", new String[]{"origin"}, new String[]{}),
        };

        // Also try configured URL if provided
        String configuredUrl = null;
        if (StringUtils.hasText(adsConfig.getIpLookupUrl())) {
            configuredUrl = adsConfig.getIpLookupUrl().trim();
            validateHttpUrl(configuredUrl, "IP lookup url");
        }

        IOException lastException = null;

        // Try configured URL first if available
        if (StringUtils.hasText(configuredUrl)) {
            try {
                IpVerificationDto result = this.attemptIpLookup4HttpClient(httpClient, configuredUrl,
                        new String[]{"ip", "query"}, new String[]{"country", "countryCode", "country_code"});
                if (result != null) {
                    log.info("IP Verification succeeded with configured URL: {}", configuredUrl);
                    result.setMatched(result.getCountryCode() != null &&
                            result.getCountryCode().equalsIgnoreCase(expectedCountryCode));
                    return result;
                }
            } catch (IOException e) {
                log.warn("Configured IP lookup URL failed: {} - {}", configuredUrl, e.getMessage());
                lastException = e;
            }
        }

        // Try each predefined endpoint
        for (IpEndpoint endpoint : endpoints) {
            try {
                IpVerificationDto result = attemptIpLookup4HttpClient(httpClient, endpoint.url,
                        endpoint.ipFields, endpoint.countryFields);
                if (result != null) {
                    log.info("IP Verification succeeded with endpoint: {} (IP: {}, Country: {})",
                            endpoint.url, result.getIp(), result.getCountryCode());
                    result.setMatched(result.getCountryCode() != null &&
                            result.getCountryCode().equalsIgnoreCase(expectedCountryCode));
                    return result;
                }
            } catch (IOException e) {
                log.debug("IP lookup endpoint {} failed: {}", endpoint.url, e.getMessage());
                lastException = e;
            }
        }

        // All endpoints failed
        if (lastException != null) {
            throw new IOException("All IP lookup endpoints failed. Last error: " + lastException.getMessage(), lastException);
        }
        throw new IOException("No valid IP lookup endpoint returned data");
    }

    /**
     * attemptIpLookup4OkHttpClient
     * @param httpClient
     * @param url
     * @param ipFieldNames
     * @param countryFieldNames
     * @return
     * @throws IOException
     */
    private IpVerificationDto attemptIpLookup4OkHttpClient(OkHttpClient httpClient, String url,
                                              String[] ipFieldNames, String[] countryFieldNames) throws IOException {
        validateHttpUrl(url, "IP lookup url");

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", Constant.DEFAULT_DESKTOP_USER_AGENT)
                .header("X-Device-Type", Constant.DEVICE_TYPE_DESK)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() < 200 || response.code() >= 300) {
                throw new IOException("IP lookup failed with status code: " + response.code() + " from " + url);
            }

            String body = response.body() != null ? response.body().string() : "";
            if (!StringUtils.hasText(body)) {
                throw new IOException("Empty response from " + url);
            }

            final JsonNode jsonNode = objectMapper.readTree(body);
            final String ip = getFirstText(jsonNode, ipFieldNames);
            final String countryCode = getFirstText(jsonNode, countryFieldNames);

            // Consider successful only if we got an IP
            if (!StringUtils.hasText(ip)) {
                throw new IOException("No IP field found in response from " + url);
            }

            IpVerificationDto result = new IpVerificationDto();
            result.setIp(ip);
            result.setCountryCode(countryCode);
            return result;
        }
    }

    private IpVerificationDto attemptIpLookup4HttpClient(HttpClient httpClient, String url,
                                                           String[] ipFieldNames, String[] countryFieldNames) throws IOException, InterruptedException {
        validateHttpUrl(url, "IP lookup url");
        HttpResponse<String> response = httpClient.send(
                buildBaseRequest(URI.create(url), null, Constant.DEVICE_TYPE_DESK).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("IP lookup failed with status code: " + response.statusCode() + " from " + url);
        }
        String body = response.body() != null ? response.body() : "";
        if (!StringUtils.hasText(body)) {
            throw new IOException("Empty response from " + url);
        }

        final JsonNode jsonNode = objectMapper.readTree(body);
        final String ip = getFirstText(jsonNode,ipFieldNames);
        final String countryCode = getFirstText(jsonNode, countryFieldNames);

        // Consider successful only if we got an IP
        if (!StringUtils.hasText(ip)) {
            throw new IOException("No IP field found in response from " + url);
        }

        IpVerificationDto result = new IpVerificationDto();
        result.setIp(ip);
        result.setCountryCode(countryCode);
        return result;
    }


    public HttpRequest.Builder buildBaseRequest(final URI uri, String userAgent, String deviceType) {
        final HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(autoTaskConfig.getRequestTimeoutMillis()))
                .header("Accept", " text/html, application/json, text/plain, */*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cache-Control","no-cache")
                .header("Pragma", "no-cache")
                .header("X-Device-Type", deviceType)
                .header("User-Agent", Optional.ofNullable(userAgent).orElse(Constant.DEFAULT_DESKTOP_USER_AGENT));
        return builder;
    }

    /**
     * Verify the URL schema, should be http or https
     * @param url
     * @param fieldName
     */
    private void validateHttpUrl(final String url, final String fieldName) {
        if (!StringUtils.hasText(url)) {
            throw new IllegalArgumentException(fieldName + " URL is required");
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new IllegalArgumentException(fieldName + " URL must use http or https scheme");
        }
    }

    /**
     *
     * @param jsonNode
     * @param fields
     * @return
     */
    private String getFirstText (final JsonNode jsonNode, final String... fields) {
        for (String field : fields) {
            final JsonNode node = jsonNode.get(field);
            if (node != null && !node.isNull() && node.isValueNode() && StringUtils.hasText(node.asText())) {
                return node.asText();
            }
        }
        return null;
    }



    public Optional<IpProxyService.ProxyConfiguration> parseProxyConfiguration(String proxyInfo) {
        if (!StringUtils.hasText(proxyInfo)) {
            return Optional.empty();
        }
        String[] userAndHost = proxyInfo.split("@", 2);
        if (userAndHost.length != 2) {
            throw new IllegalArgumentException("dynamicProxyInfo must use user:password@host:port format");
        }
        String[] credentials = userAndHost[0].split(":", 2);
        if (credentials.length != 2 || !StringUtils.hasText(credentials[0]) || !StringUtils.hasText(credentials[1])) {
            throw new IllegalArgumentException("dynamicProxyInfo credentials must use user:password format");
        }
        String[] hostAndPort = userAndHost[1].split(":", 2);
        if (hostAndPort.length != 2 || !StringUtils.hasText(hostAndPort[0]) || !StringUtils.hasText(hostAndPort[1])) {
            throw new IllegalArgumentException("dynamicProxyInfo host must use host:port format");
        }
        try {
            return Optional.of(new IpProxyService.ProxyConfiguration(
                    credentials[0].trim(),
                    credentials[1].trim(),
                    hostAndPort[0].trim(),
                    Integer.parseInt(hostAndPort[1].trim())));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("dynamicProxyInfo port must be numeric", ex);
        }
    }


    /**
     * Build OkHttpClient
     * @param dynamicProxyInfo
     * @return
     */
    public OkHttpClient buildOkHttpClient(String dynamicProxyInfo) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(adsConfig.getConnectTimeoutMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(adsConfig.getRequestTimeoutMillis(), TimeUnit.MILLISECONDS);

        if (dynamicProxyInfo == null || !StringUtils.hasText(dynamicProxyInfo)) {
            return builder.build();
        }

        Optional<ProxyConfiguration> p = parseProxyConfiguration(dynamicProxyInfo); // user:pass@host:port
        if (p.isEmpty()) {
            return builder.build();
        }

        log.info("Building OkHttpClient with SOCKS5 proxy: {}: port: {} user name: {})",
                p.get().host(), p.get().port(), p.get().username);

        // For SOCKS5, use Java Authenticator (RFC 1929) instead of HTTP Basic Auth
        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                log.debug("Authenticator called for {}:{} (type: {})",
                        getRequestingHost(), getRequestingPort(), getRequestorType());
                if (getRequestorType() == RequestorType.PROXY ||
                        getRequestorType() == RequestorType.SERVER) {
                    log.debug("Providing SOCKS5 credentials for {}:{}",
                            getRequestingHost(), getRequestingPort());
                    return new PasswordAuthentication(
                            p.get().username(),
                            p.get().password().toCharArray()
                    );
                }
                return null;
            }
        });
        // Use SOCKS5 proxy with OkHttp (supports SOCKS5 natively)
        builder.proxy(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(p.get().host(), p.get().port())));

        return builder.build();
    }


    public HttpClient buildHttpClient(String dynamicProxyInfo) {
        // Allow Basic auth for HTTP proxy CONNECT (HTTPS tunneling) and plain HTTP proxying.
        // Without this, JDK HttpClient may refuse to send proxy credentials and return 407.
        System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
        System.setProperty("jdk.http.auth.proxying.disabledSchemes", "");
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(adsConfig.getConnectTimeoutMillis()))
                .followRedirects(HttpClient.Redirect.NORMAL);
        if (dynamicProxyInfo == null || !StringUtils.hasText(dynamicProxyInfo)) {
            return builder.build();
        }

        Optional<ProxyConfiguration> p = parseProxyConfiguration(dynamicProxyInfo); // user:pass@host:port
        if (p.isEmpty()) {
            return builder.build();
        }
        log.info("Building HttpClient with HTTPS proxy: {}: port: {} user name: {})",
                p.get().host(), p.get().port(), p.get().username);

        // Use per-client Authenticator to avoid cross-task/global credential overrides.
        Authenticator proxyAuthenticator = new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                log.debug("Authenticator called for {}:{} (type: {})",
                        getRequestingHost(), getRequestingPort(), getRequestorType());
                if (getRequestorType() == RequestorType.PROXY) {
                    log.debug("Providing HTTPS credentials for {}:{}",
                            getRequestingHost(), getRequestingPort());
                    return new PasswordAuthentication(
                            p.get().username(),
                            p.get().password().toCharArray()
                    );
                }
                return null;
            }
        };
        builder.authenticator(proxyAuthenticator);
        // java.net.http.HttpClient expects a ProxySelector instead of java.net.Proxy.
        builder.proxy(ProxySelector.of(new InetSocketAddress(p.get().host(), p.get().port())));
        return builder.build();
    }


    public Request buildOkHttpClientBaseRequest(final String uri, String userAgent, String deviceType) {
        Request request = new Request.Builder()
                .url(uri)
                .header("Accept", "text/html, application/json, text/plain, */*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .header("X-Device-Type", deviceType)
                .header("User-Agent", Optional.ofNullable(userAgent).orElse(Constant.DEFAULT_DESKTOP_USER_AGENT))
                .get()
                .build();
        return request;
    }

    private record ProxyConfiguration(String username, String password, String host, int port) {
    }

    // Define multiple IP lookup endpoints
    class IpEndpoint {
        String url;
        String[] ipFields;
        String[] countryFields;
        IpEndpoint(String url, String[] ipFields, String[] countryFields) {
            this.url = url;
            this.ipFields = ipFields;
            this.countryFields = countryFields;
        }
    }


}
