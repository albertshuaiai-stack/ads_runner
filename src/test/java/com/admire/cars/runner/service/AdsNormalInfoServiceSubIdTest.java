package com.admire.cars.runner.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Affiliate URL 的 subid={subid} 占位符规则 / subid={subid} placeholder rules for affiliate URLs
 */
class AdsNormalInfoServiceSubIdTest {

    @Test
    void appendsPlaceholderWhenMissing() {
        assertEquals("https://www.bonusarrive.com/link?ad=1&c=2&subid={subid}",
                AdsNormalInfoService.ensureSubIdPlaceholder("https://www.bonusarrive.com/link?ad=1&c=2"));
    }

    @Test
    void appendsAfterQuestionMarkWhenNoQueryYet() {
        assertEquals("https://www.bonusarrive.com/link?subid={subid}",
                AdsNormalInfoService.ensureSubIdPlaceholder("https://www.bonusarrive.com/link"));
    }

    @Test
    void rewritesExistingPlainValueInsteadOfAppending() {
        assertEquals("https://www.bonusarrive.com/link?ad=1&subid={subid}&url=",
                AdsNormalInfoService.ensureSubIdPlaceholder(
                        "https://www.bonusarrive.com/link?ad=1&subid=1002&url="));
    }

    @Test
    void keepsExistingPlaceholderUnchanged() {
        assertEquals("https://www.bonusarrive.com/link?ad=164043&c=4216&subid={subid}&sub2id=&url=",
                AdsNormalInfoService.ensureSubIdPlaceholder(
                        "https://www.bonusarrive.com/link?ad=164043&c=4216&subid={subid}&sub2id=&url="));
    }

    @Test
    void caseInsensitiveParamNameIsRewritten() {
        assertEquals("https://www.bonusarrive.com/link?ad=1&subid={subid}",
                AdsNormalInfoService.ensureSubIdPlaceholder("https://www.bonusarrive.com/link?ad=1&SubId=abc"));
    }

    @Test
    void dropsDuplicateSubidParamsAndKeepsOne() {
        assertEquals("https://www.bonusarrive.com/link?ad=1&subid={subid}&url=",
                AdsNormalInfoService.ensureSubIdPlaceholder(
                        "https://www.bonusarrive.com/link?ad=1&subid=1002&url=&subid={subid}"));
    }

    @Test
    void rewritesSubIdAliasAndKeepsSub2id() {
        // sub_id= 视为同一参数, 改成 subid={subid}; sub2id= 是另一个参数, 不动 / sub_id is the same param; sub2id is separate
        assertEquals("https://www.bonusarrive.com/link?ad=1&subid={subid}&sub2id=x",
                AdsNormalInfoService.ensureSubIdPlaceholder(
                        "https://www.bonusarrive.com/link?ad=1&sub_id=y&sub2id=x"));
    }

    @Test
    void subIdAliasAndSubidTogetherKeepOnlyOne() {
        assertEquals("https://www.bonusarrive.com/link?ad=1&subid={subid}&url=",
                AdsNormalInfoService.ensureSubIdPlaceholder(
                        "https://www.bonusarrive.com/link?ad=1&subid=1002&url=&sub_id=abc"));
    }

    @Test
    void blankUrlReturnsAsIs() {
        assertNull(AdsNormalInfoService.ensureSubIdPlaceholder(null));
        assertEquals("", AdsNormalInfoService.ensureSubIdPlaceholder(""));
        assertEquals("  ", AdsNormalInfoService.ensureSubIdPlaceholder("  "));
    }

    @Test
    void urlTooLongAfterAppendKeepsOriginal() {
        String url = "https://www.bonusarrive.com/link?ad=" + "x".repeat(120);
        assertEquals(url, AdsNormalInfoService.ensureSubIdPlaceholder(url));
    }
}
