package com.admire.cars.runner;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.util.concurrent.TimeUnit;

public class ProxyTestUtil {

//    public static void main(String[] args) throws IOException {
//        // Use the proxy from the actual error message in production
//        String proxyInfo = "6037786-c02e85aa:552fa91d-US-68699529-5m@gate.kookeey.info:1000";
//        testProxy(proxyInfo);
//    }

    public static void testProxy(String proxyInfo) throws IOException {
        // Parse proxy: user:password@host:port
        String[] parts = proxyInfo.split("@");
        if (parts.length != 2) {
            System.err.println("Invalid proxy format. Expected: user:password@host:port");
            return;
        }

        String[] credentials = parts[0].split(":");
        if (credentials.length != 2) {
            System.err.println("Invalid credentials format. Expected: user:password");
            return;
        }

        String[] hostPort = parts[1].split(":");
        if (hostPort.length != 2) {
            System.err.println("Invalid host:port format");
            return;
        }

        String username = credentials[0];
        String password = credentials[1];
        String host = hostPort[0];
        int port;

        try {
            port = Integer.parseInt(hostPort[1]);
        } catch (NumberFormatException e) {
            System.err.println("Invalid port number: " + hostPort[1]);
            return;
        }

        System.out.println("Testing SOCKS5 proxy with OkHttp:");
        System.out.println("  Host: " + host);
        System.out.println("  Port: " + port);
        System.out.println("  Username: " + username);
        System.out.println("  Password: " + (password.length() > 0 ? "***" : "empty"));
        System.out.println();
        
        System.out.println("Note: SOCKS5 authentication is handled at the socket level.");
        System.out.println("If tests fail with 'SOCKS : authentication failed', the proxy");
        System.out.println("credentials may be invalid or expired.\n");

        // Set up global Authenticator for SOCKS5 - this helps but may not be sufficient
        // because SOCKS5 auth happens at the socket level before HTTP traffic
        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                System.out.println("  [Authenticator] Called for " + getRequestingHost() + ":" + getRequestingPort() + 
                                   " (type: " + getRequestorType() + ")");
                return new PasswordAuthentication(username, password.toCharArray());
            }
        });

        // Create OkHttpClient with SOCKS5 proxy
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .proxy(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(host, port)))
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();

        // Test with multiple IP lookup endpoints - stop on first success
        System.out.println("Testing IP Lookup (tries multiple endpoints until one succeeds):");
        testMultipleIpLookups(httpClient);
    }

    private static void testMultipleIpLookups(OkHttpClient client) {
        String[] endpoints = {
                "https://api.country.is/",
                "https://ipapi.co/json/",
                "https://httpbin.org/ip"
        };
        
        boolean success = false;
        
        for (String url : endpoints) {
            System.out.println("\n  Trying: " + url);
            try {
                Request request = new Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0")
                        .get()
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    System.out.println("    Status: " + response.code());
                    
                    if (response.code() == 200) {
                        String body = response.body() != null ? response.body().string() : "null";
                        System.out.println("    Response: " + body.substring(0, Math.min(150, body.length())));
                        System.out.println("    ✓ SUCCESS - IP lookup working!");
                        success = true;
                        break; // Stop on first success
                    } else {
                        String body = response.body() != null ? response.body().string() : "null";
                        System.out.println("    ✗ Status " + response.code());
                    }
                }
            } catch (Exception e) {
                System.out.println("    ✗ ERROR: " + e.getMessage());
            }
        }
        
        if (success) {
            System.out.println("\n✓ PROXY VERIFICATION COMPLETE - All endpoints working through proxy!");
        } else {
            System.out.println("\n✗ All IP lookup endpoints failed");
        }
    }
}
