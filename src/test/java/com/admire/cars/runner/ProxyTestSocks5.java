package com.admire.cars.runner;

import org.junit.jupiter.api.Test;

public class ProxyTestSocks5 {

    @Test
    public void testSocks5Proxy() throws Exception {
        String proxyInfo = "6037786-c02e85aa:552fa91d-US-37535291-5m@gate-us.kookeey.info:1000";
        ProxyTestUtil.testProxy(proxyInfo);
    }
}
