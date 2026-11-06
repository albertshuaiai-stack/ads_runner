package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.GmailPlatformLink;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// 唯一 ID 归类匹配测试 / unique match id classification tests
class GmailPlatformLinkServiceTest {

    private GmailPlatformLink rule(String matchId) {
        GmailPlatformLink rule = new GmailPlatformLink();
        rule.setMatchId(matchId);
        return rule;
    }

    @Test
    void matchesByExtractedUserIdHead() {
        GmailPlatformLink sample = rule("151919334");
        // u1 前段用户 ID 一致，点击 ID 和商家都变了 / same user id head, click id and merchant changed
        String uploaded = "https://click.linksynergy.com/deeplink?id=d2nUJiCPCGM&mid=99999&murl=https%3A%2F%2Fwww.nike.com%2F&u1=151919334x9999999999";

        GmailPlatformLink matched = GmailPlatformLinkService.matchRule(List.of(sample), uploaded);
        assertNotNull(matched);
        assertSame(sample, matched);
    }

    @Test
    void rejectsDifferentUserIdHead() {
        GmailPlatformLink sample = rule("151919334");
        String uploaded = "https://click.linksynergy.com/deeplink?id=d2nUJiCPCGM&mid=43317&u1=987654321x3522333213";

        assertNull(GmailPlatformLinkService.matchRule(List.of(sample), uploaded));
    }

    @Test
    void matchesBySubstringFallback() {
        GmailPlatformLink sample = rule("d2nUJiCPCGM");
        String uploaded = "https://click.linksynergy.com/deeplink?id=d2nUJiCPCGM&mid=43317";

        assertNotNull(GmailPlatformLinkService.matchRule(List.of(sample), uploaded));
    }

    @Test
    void prefersLongestMatchId() {
        GmailPlatformLink generic = rule("151919334");
        GmailPlatformLink specific = rule("151919334x3522");
        String uploaded = "https://click.linksynergy.com/deeplink?u1=151919334x3522333213";

        GmailPlatformLink matched = GmailPlatformLinkService.matchRule(List.of(generic, specific), uploaded);
        assertSame(specific, matched);
    }

    @Test
    void matchesCjLinkByCampaignCodeSubstring() {
        GmailPlatformLink sample = rule("1720273");
        String uploaded = "https://us.puma.com/us/en?cjdata=MXxOfDB8&cjevent=aaff4487&click_id=aaff4487&utm_source=CJ_COM&utm_campaigncode=1720273";

        assertNotNull(GmailPlatformLinkService.matchRule(List.of(sample), uploaded));
    }

    @Test
    void rejectsWhenIdAbsent() {
        GmailPlatformLink sample = rule("151919334");
        String uploaded = "https://click.linksynergy.com/deeplink?id=abc&mid=43317";

        assertNull(GmailPlatformLinkService.matchRule(List.of(sample), uploaded));
    }

    @Test
    void handlesBlankInput() {
        assertNull(GmailPlatformLinkService.matchRule(List.of(rule("151919334")), null));
        assertNull(GmailPlatformLinkService.matchRule(List.of(rule("151919334")), "  "));
        assertNull(GmailPlatformLinkService.matchRule(List.of(), "https://a.com/?u1=151919334x1"));
        assertNull(GmailPlatformLinkService.matchRule(null, "https://a.com/?u1=151919334x1"));
    }
}
