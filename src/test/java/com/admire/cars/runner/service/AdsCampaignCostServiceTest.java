package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AdsCampaignCost;
import com.admire.cars.runner.repository.AdsCampaignCostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 花费台账: 删任务/删广告/重建广告后历史投入不丢 / the ledger keeps history across deletions and recreations
 */
class AdsCampaignCostServiceTest {

    private static final String OWNER = "18565545084";

    private final List<AdsCampaignCost> store = new ArrayList<>();
    private AdsCampaignCostService service;

    @BeforeEach
    void setUp() {
        AdsCampaignCostRepository repository = mock(AdsCampaignCostRepository.class);
        when(repository.findByAdsOwnerAndCampaignKey(anyString(), anyString())).thenAnswer(invocation -> store.stream()
                .filter(row -> row.getAdsOwner().equals(invocation.getArgument(0))
                        && row.getCampaignKey().equals(invocation.getArgument(1)))
                .findFirst());
        when(repository.existsByAdsOwnerAndCampaignNameAndCampaignIdIsNotNull(anyString(), anyString()))
                .thenAnswer(invocation -> store.stream().anyMatch(row -> row.getAdsOwner().equals(invocation.getArgument(0))
                        && row.getCampaignName().equals(invocation.getArgument(1)) && row.getCampaignId() != null));
        when(repository.save(any(AdsCampaignCost.class))).thenAnswer(invocation -> persist(invocation.getArgument(0)));
        when(repository.saveAndFlush(any(AdsCampaignCost.class))).thenAnswer(invocation -> persist(invocation.getArgument(0)));
        service = new AdsCampaignCostService(repository);
    }

    private AdsCampaignCost persist(AdsCampaignCost row) {
        if (!store.contains(row)) {
            store.add(row);
        }
        return row;
    }

    private AdsCampaignCost row(String key) {
        return store.stream().filter(item -> item.getCampaignKey().equals(key)).findFirst().orElseThrow();
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    @Test
    void campaignIdRowIsUpdatedInPlace() {
        service.record(OWNER, "111", "guess", "ENABLED", money("10"), "cny", 28L);
        service.record(OWNER, "111", "guess", "PAUSED", money("12.5"), "CNY", null);

        assertEquals(1, store.size());
        AdsCampaignCost row = row("ID:111");
        assertEquals(0, money("12.5").compareTo(row.getLifetimeCost()));
        assertEquals("CNY", row.getCostCurrency());
        assertEquals("PAUSED", row.getCampaignStatus());
        // 任务绑定在后续未匹配时保留 / the task binding is kept when a later report has no task
        assertEquals(28L, row.getNormalAdsId());
    }

    @Test
    void recreatedCampaignWithSameNameKeepsOldCost() {
        service.record(OWNER, "111", "guess", "ENABLED", money("80"), "CNY", 28L);
        // 在 Google Ads 删除后用同名重建, 新 id 从 0 开始 / removed and recreated with the same name
        service.record(OWNER, "222", "guess", "ENABLED", money("3"), "CNY", 28L);

        assertEquals(2, store.size());
        assertEquals(0, money("80").compareTo(row("ID:111").getLifetimeCost()));
        assertEquals(0, money("3").compareTo(row("ID:222").getLifetimeCost()));
    }

    @Test
    void firstIdReportAdoptsLegacyRowOfSameCampaign() {
        service.record(OWNER, null, "momcozy", null, money("97.77"), "CNY", 11L);
        // 无效点击退款会让累计花费略降, 仍视为同一 campaign / small credits still count as the same campaign
        service.record(OWNER, "555", "momcozy", "ENABLED", money("97.10"), "CNY", 11L);

        assertEquals(1, store.size());
        AdsCampaignCost row = row("ID:555");
        assertEquals("555", row.getCampaignId());
        assertEquals(0, money("97.10").compareTo(row.getLifetimeCost()));
    }

    @Test
    void firstIdReportKeepsLegacyRowOfRemovedCampaign() {
        service.record(OWNER, null, "intimissimi", null, money("55.33"), "CNY", 26L);
        service.record(OWNER, "777", "intimissimi", "ENABLED", money("2"), "CNY", 30L);

        assertEquals(2, store.size());
        assertEquals(0, money("55.33").compareTo(row("NAME:intimissimi").getLifetimeCost()));
        assertEquals(0, money("2").compareTo(row("ID:777").getLifetimeCost()));
    }

    @Test
    void oldScriptRowIsIgnoredOnceIdRowsExist() {
        service.record(OWNER, "555", "momcozy", "ENABLED", money("100"), "CNY", 11L);
        service.record(OWNER, null, "momcozy", null, money("100"), "CNY", 11L);

        assertEquals(1, store.size());
        assertNull(store.stream().filter(item -> item.getCampaignKey().startsWith("NAME:")).findFirst().orElse(null));
    }

    @Test
    void oldScriptArchivesRowWhenCampaignWasRecreated() {
        service.record(OWNER, null, "protonvpn", null, money("21.87"), "CNY", 25L);
        service.record(OWNER, null, "protonvpn", null, money("0.40"), "CNY", 29L);

        assertEquals(2, store.size());
        AdsCampaignCost archived = store.stream()
                .filter(item -> item.getCampaignKey().startsWith("NAME:protonvpn@")).findFirst().orElseThrow();
        assertEquals(0, money("21.87").compareTo(archived.getLifetimeCost()));
        assertEquals("REPLACED", archived.getCampaignStatus());
        assertEquals(25L, archived.getNormalAdsId());
        AdsCampaignCost current = row("NAME:protonvpn");
        assertEquals(0, money("0.40").compareTo(current.getLifetimeCost()));
        assertEquals(29L, current.getNormalAdsId());
    }

    @Test
    void oldScriptSmallCreditDoesNotArchive() {
        service.record(OWNER, null, "momcozy", null, money("81.38"), "CNY", 11L);
        service.record(OWNER, null, "momcozy", null, money("80.69"), "CNY", 11L);

        assertEquals(1, store.size());
        assertEquals(0, money("80.69").compareTo(row("NAME:momcozy").getLifetimeCost()));
    }

    @Test
    void invalidInputIsIgnored() {
        service.record(OWNER, "1", "guess", "ENABLED", null, "CNY", null);
        service.record(OWNER, "1", "guess", "ENABLED", money("-1"), "CNY", null);
        service.record(OWNER, "1", " ", "ENABLED", money("1"), "CNY", null);

        assertTrue(store.isEmpty());
    }

    @Test
    void groupByCampaignIsCaseInsensitive() {
        AdsCampaignCost upper = new AdsCampaignCost();
        upper.setAdsOwner(OWNER);
        upper.setCampaignName("Guess");
        AdsCampaignCost lower = new AdsCampaignCost();
        lower.setAdsOwner(OWNER);
        lower.setCampaignName("guess ");

        assertEquals(1, AdsCampaignCostService.groupByCampaign(List.of(upper, lower)).size());
        assertEquals(2, AdsCampaignCostService.groupByCampaign(List.of(upper, lower))
                .get(AdsCampaignCostService.matchKey(OWNER, "GUESS")).size());
    }
}
