package com.admire.cars.runner.job;

import com.admire.cars.runner.util.AdsHttpClientTool;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void enrichAffiliateUrlForNormal_injectsClickIdEvenWithoutPlaceholder() throws Exception {
        AdsHttpClientTool tool = new AdsHttpClientTool();
        Method method = AdsHttpClientTool.class.getDeclaredMethod(
                "enrichAffiliateUrlForNormal", String.class, String.class, Long.class);
        method.setAccessible(true);
        String clickId = "11111111-2222-3333-4444-555555555555";

        String replaced = (String) method.invoke(tool,
                "https://www.bonusarrive.com/clink?a=319&c=429&subid=FIXED", clickId, 7L);
        assertTrue(replaced.contains("&subid=" + clickId));
        assertFalse(replaced.contains("FIXED"));

        String appended = (String) method.invoke(tool,
                "https://www.bonusarrive.com/clink?a=319&c=429", clickId, 7L);
        assertTrue(appended.endsWith("&subid=" + clickId));

        String placeholder = (String) method.invoke(tool,
                "https://www.bonusarrive.com/clink?a=319&subid={subid}&sub_id2={adid}", clickId, 7L);
        assertTrue(placeholder.contains("subid=" + clickId));
        assertTrue(placeholder.contains("sub_id2=n7"));
    }
}
