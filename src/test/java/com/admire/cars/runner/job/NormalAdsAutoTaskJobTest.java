package com.admire.cars.runner.job;

import com.admire.cars.runner.util.AdsHttpClientTool;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NormalAdsAutoTaskJobTest {

    @Test
    void enrichAffiliateUrl_replacesSubidPlaceholder() throws Exception {
        AdsHttpClientTool tool = new AdsHttpClientTool();
        Method method = AdsHttpClientTool.class.getDeclaredMethod("enrichAffiliateUrl", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(tool, "https://example.com/click?subid={subid}");

        assertTrue(result.startsWith("https://example.com/click?subid="));
        assertNotEquals("https://example.com/click?subid={subid}", result);
        assertTrue(result.matches("https://example\\.com/click\\?subid=[0-9a-fA-F\\-]{36}"));
    }
}
