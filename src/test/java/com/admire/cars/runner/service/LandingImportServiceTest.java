package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AdsPlatform;
import com.admire.cars.runner.entity.GmailPlatformLink;
import com.admire.cars.runner.entity.ShiftLink;
import com.admire.cars.runner.entity.ToolEmail;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.AdsPlatformRepository;
import com.admire.cars.runner.repository.GmailPlatformLinkRepository;
import com.admire.cars.runner.repository.ShiftLinkHistoryRepository;
import com.admire.cars.runner.repository.ShiftLinkRepository;
import com.admire.cars.runner.repository.ShiftLinkStatRepository;
import com.admire.cars.runner.repository.ToolAccountRepository;
import com.admire.cars.runner.repository.ToolEmailRepository;
import com.admire.cars.runner.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LandingImportServiceTest {

    @Mock private ShiftLinkService shiftLinkService;
    @Mock private GmailPlatformLinkService gmailPlatformLinkService;
    @Mock private ShiftLinkRepository shiftLinkRepository;
    @Mock private GmailPlatformLinkRepository gmailPlatformLinkRepository;
    @Mock private ToolEmailRepository toolEmailRepository;
    @Mock private AdsPlatformRepository adsPlatformRepository;
    @Mock private UserRepository userRepository;

    private LandingImportService service;

    @BeforeEach
    void setUp() {
        service = new LandingImportService(shiftLinkService, gmailPlatformLinkService, shiftLinkRepository,
                gmailPlatformLinkRepository, toolEmailRepository, adsPlatformRepository, userRepository);
        User owner = new User();
        owner.setId(1L);
        owner.setUserPhoneNumber("18565545084");
        org.mockito.Mockito.lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
    }

    private ToolEmail email(String userName) {
        ToolEmail email = new ToolEmail();
        email.setUserName(userName);
        email.setEmailAddress(userName + "@gmail.com");
        return email;
    }

    private GmailPlatformLink rule(String status) {
        GmailPlatformLink rule = new GmailPlatformLink();
        rule.setUserName("codekawoth");
        rule.setPlatformName("topcashback");
        rule.setMatchId("GL000001");
        rule.setStatus(status);
        return rule;
    }

    private void platformExists() {
        AdsPlatform platform = new AdsPlatform();
        platform.setPlatformName("topcashback");
        when(adsPlatformRepository.findByPlatformNameIgnoreCase("topcashback")).thenReturn(Optional.of(platform));
    }

    @Test
    void importsLinks_createsMissingRule_andSkipsDuplicates() {
        platformExists();
        when(toolEmailRepository.findByUserName("codekawoth")).thenReturn(Optional.of(email("codekawoth")));
        when(gmailPlatformLinkRepository.findByAdsOwnerAndUserNameIgnoreCaseAndPlatformNameIgnoreCase(
                "18565545084", "codekawoth", "topcashback")).thenReturn(List.of());
        when(gmailPlatformLinkService.ensureRule("18565545084", "codekawoth", "topcashback"))
                .thenReturn(rule(GmailPlatformLink.STATUS_RUNNING));
        String fresh = "https://usa.tommy.com/en?ranSiteID=abc-1";
        String dup = "https://usa.tommy.com/en?ranSiteID=abc-2";
        when(shiftLinkRepository.findByAdsOwnerAndFullUrl("18565545084", fresh)).thenReturn(List.of());
        when(shiftLinkRepository.findByAdsOwnerAndFullUrl("18565545084", dup)).thenReturn(List.of(new ShiftLink()));

        LandingImportService.ImportResult result = service.importEntries(1L, "Normal", null, List.of(
                new LandingImportService.Entry("codekawoth", "topcashback", "Tommy Hilfiger", List.of(fresh, dup, "  "))));

        assertEquals(1, result.imported());
        assertEquals(1, result.duplicated());
        assertEquals(1, result.createdRules().size());
        ArgumentCaptor<ShiftLink> captor = ArgumentCaptor.forClass(ShiftLink.class);
        verify(shiftLinkService, times(1)).createShiftLink(captor.capture(), eq(1L));
        ShiftLink saved = captor.getValue();
        assertEquals("codekawoth", saved.getUserName());
        assertEquals("Tommy Hilfiger", saved.getAdsName());
        assertEquals("https://usa.tommy.com/en", saved.getLandingPageUrl());
        assertEquals("?ranSiteID=abc-1", saved.getUrlSuffix());
    }

    @Test
    void skipsDisabledRule() {
        platformExists();
        when(toolEmailRepository.findByUserName("codekawoth")).thenReturn(Optional.of(email("codekawoth")));
        when(gmailPlatformLinkRepository.findByAdsOwnerAndUserNameIgnoreCaseAndPlatformNameIgnoreCase(
                anyString(), anyString(), anyString())).thenReturn(List.of(rule(GmailPlatformLink.STATUS_DISABLED)));
        when(gmailPlatformLinkService.ensureRule("18565545084", "codekawoth", "topcashback"))
                .thenReturn(rule(GmailPlatformLink.STATUS_DISABLED));

        LandingImportService.ImportResult result = service.importEntries(1L, "Normal", null, List.of(
                new LandingImportService.Entry("codekawoth", "topcashback", "Nike", List.of("https://nike.com/?a=1"))));

        assertEquals(0, result.imported());
        assertEquals(1, result.skippedEntries());
        assertEquals("gmail link rule is DISABLED", result.entries().get(0).skippedReason());
        verify(shiftLinkService, never()).createShiftLink(any(), any());
    }

    @Test
    void skipsUnknownGmail() {
        when(toolEmailRepository.findByUserName("nobody")).thenReturn(Optional.empty());
        when(toolEmailRepository.findByEmailAddress("nobody")).thenReturn(Optional.empty());

        LandingImportService.ImportResult result = service.importEntries(1L, "Normal", null, List.of(
                new LandingImportService.Entry("nobody", "topcashback", "Nike", List.of("https://nike.com/?a=1"))));

        assertEquals(1, result.skippedEntries());
        verify(gmailPlatformLinkService, never()).ensureRule(any(), any(), any());
    }

    @Test
    void nextAutoMatchId_incrementsHighestGlSequence() {
        GmailPlatformLinkService rules = new GmailPlatformLinkService(gmailPlatformLinkRepository,
                toolEmailRepository, org.mockito.Mockito.mock(ShiftLinkStatRepository.class),
                org.mockito.Mockito.mock(ToolAccountRepository.class), userRepository,
                org.mockito.Mockito.mock(ShiftLinkHistoryRepository.class));
        GmailPlatformLink a = new GmailPlatformLink();
        a.setMatchId("GL000007");
        GmailPlatformLink b = new GmailPlatformLink();
        b.setMatchId("GLOBAL-x");
        when(gmailPlatformLinkRepository.findByMatchIdStartingWith("GL")).thenReturn(List.of(a, b));

        assertEquals("GL000008", rules.nextAutoMatchId());
        assertNull(GmailPlatformLinkService.matchRule(List.of(a), "https://nike.com/?u1=151919334x1&id=7"));
    }
}
