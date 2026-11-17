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

    // 空的 sub2id 写入同一个 clickId, 有业务值(如 n<taskId>)的不动
    // an empty sub2id is mirrored with the same click id; real values stay untouched
    @Test
    void enrichAffiliateUrlForNormal_fillsEmptySub2idWithSameClickId() throws Exception {
        AdsHttpClientTool tool = new AdsHttpClientTool();
        Method method = AdsHttpClientTool.class.getDeclaredMethod(
                "enrichAffiliateUrlForNormal", String.class, String.class, Long.class);
        method.setAccessible(true);
        String clickId = "11111111-2222-3333-4444-555555555555";

        String filled = (String) method.invoke(tool,
                "https://www.bonusarrive.com/link?ad=137072&c=4216&subid={subid}&sub2id=&url=", clickId, 9L);
        assertTrue(filled.contains("subid=" + clickId));
        assertTrue(filled.contains("sub2id=" + clickId));
        assertTrue(filled.endsWith("&url="));

        String aliasFilled = (String) method.invoke(tool,
                "https://www.bonusarrive.com/link?ad=1&subid={subid}&sub_id2=", clickId, 9L);
        assertTrue(aliasFilled.contains("sub_id2=" + clickId));

        String kept = (String) method.invoke(tool,
                "https://www.bonusarrive.com/link?ad=1&subid={subid}&sub2id=n9", clickId, 9L);
        assertTrue(kept.contains("sub2id=n9"));
        assertFalse(kept.contains("sub2id=" + clickId));

        String absent = (String) method.invoke(tool,
                "https://www.bonusarrive.com/link?ad=1&subid={subid}", clickId, 9L);
        assertFalse(absent.contains("sub2id="));
    }
}
