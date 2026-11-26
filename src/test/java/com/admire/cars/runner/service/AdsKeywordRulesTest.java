package com.admire.cars.runner.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdsKeywordRulesTest {

    @Test
    void stripsAffiliateAndRegionSuffixesFromNames() {
        assertEquals("melissa", AdsKeywordRules.stripNameSuffix("MelissaAffiliatePartners"));
        assertEquals("castlery", AdsKeywordRules.stripNameSuffix("CastleryUS"));
        assertEquals("vevor", AdsKeywordRules.stripNameSuffix("VevorManyGEOs"));
        assertEquals("xppen", AdsKeywordRules.stripNameSuffix("XPPENManyGeos"));
        assertEquals("rayneo", AdsKeywordRules.stripNameSuffix("RayNeoAffiliateProgram"));
        assertEquals("shein", AdsKeywordRules.stripNameSuffix("SheinAPPWebOnelink"));
        assertEquals("1800contacts", AdsKeywordRules.stripNameSuffix("1800ContactsUS"));
        assertEquals("taskrabbit", AdsKeywordRules.stripNameSuffix("TaskrabbitNorthAmerica"));
        assertEquals("intimissimi", AdsKeywordRules.stripNameSuffix("Intimissimi [P]"));
        assertEquals("moen", AdsKeywordRules.stripNameSuffix("Moen"));
        // 只剩一个词时不再去后缀 / a single word is kept even if it is a suffix word
        assertEquals("shop", AdsKeywordRules.stripNameSuffix("Shop"));
    }

    @Test
    void campaignNameWithSuffixStillMatchesBrandSearches() {
        List<String> tokens = AdsKeywordRules.brandTokens(null, "MelissaAffiliatePartners", null);

        assertTrue(AdsKeywordRules.containsBrand("mini melissa", tokens));
        assertTrue(AdsKeywordRules.containsBrand("melissa shoes", tokens));
        assertFalse(AdsKeywordRules.containsBrand("jelly sandals", tokens));
    }

    @Test
    void shopNamesAddBrandTokens() {
        List<String> tokens = AdsKeywordRules.brandTokens(null, "xppen", null, "XP PEN Many Geos");

        assertTrue(AdsKeywordRules.containsBrand("xp pen artist 12", tokens));
    }

    @Test
    void planBrandNameOverridesFallbacks() {
        List<String> tokens = AdsKeywordRules.brandTokens("smith, smith teamaker", "smithtea", null, "Smith Teamaker");

        assertEquals(List.of("smith", "smithteamaker"), tokens);
    }
}
