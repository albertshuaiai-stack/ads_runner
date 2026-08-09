package com.admire.cars.runner.service;

import com.admire.cars.runner.dto.dashboard.BalanceByPlatformItem;
import com.admire.cars.runner.dto.dashboard.BalanceByUserNameItem;
import com.admire.cars.runner.dto.dashboard.CashBackAccountByPlatformItem;
import com.admire.cars.runner.dto.dashboard.CashBackAccountByUserNameItem;
import com.admire.cars.runner.dto.dashboard.DashboardCashBackAccountResponse;
import com.admire.cars.runner.dto.dashboard.DashboardShiftLinkResponse;
import com.admire.cars.runner.dto.dashboard.IncomeExpenditureItem;
import com.admire.cars.runner.dto.dashboard.ShiftLinkMatrixDashboardItem;
import com.admire.cars.runner.dto.dashboard.ShiftLinkNormalDashboardItem;
import com.admire.cars.runner.entity.ShiftLink;
import com.admire.cars.runner.entity.ToolAccount;
import com.admire.cars.runner.entity.ToolIncome;
import com.admire.cars.runner.entity.ToolOutcome;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final String TARGET_CURRENCY = "USD";

    private final ShiftLinkService shiftLinkService;
    private final ToolAccountService toolAccountService;
    private final ToolIncomeService toolIncomeService;
    private final ToolOutcomeService toolOutcomeService;
    private final CurrencyExchangeService currencyExchangeService;

    public DashboardService(
            ShiftLinkService shiftLinkService,
            ToolAccountService toolAccountService,
            ToolIncomeService toolIncomeService,
            ToolOutcomeService toolOutcomeService,
            CurrencyExchangeService currencyExchangeService) {
        this.shiftLinkService = shiftLinkService;
        this.toolAccountService = toolAccountService;
        this.toolIncomeService = toolIncomeService;
        this.toolOutcomeService = toolOutcomeService;
        this.currencyExchangeService = currencyExchangeService;
    }

    public DashboardShiftLinkResponse getShiftLinkDashboard(Long currentUserId) {
        List<ShiftLink> shiftLinks = shiftLinkService.getAllShiftLinks(currentUserId);

        List<ShiftLinkNormalDashboardItem> normal = buildNormalDashboard(shiftLinks);
        List<ShiftLinkMatrixDashboardItem> matrix = buildMatrixDashboard(shiftLinks);

        return new DashboardShiftLinkResponse(normal, matrix);
    }

    public DashboardCashBackAccountResponse getCashBackAccountDashboard(Long currentUserId) {
        List<ToolAccount> accounts = toolAccountService.findAllForUser(currentUserId);

        List<CashBackAccountByUserNameItem> byUserName = accounts.stream()
                .filter(a -> StringUtils.hasText(a.getUserName()))
                .collect(Collectors.groupingBy(ToolAccount::getUserName))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .map(entry -> {
                    List<ToolAccount> group = entry.getValue();
                    return new CashBackAccountByUserNameItem(
                            entry.getKey(),
                            group.size(),
                            countByStatus(group, "RUNNING"),
                            countByStatus(group, "PAUSED"),
                            countByStatus(group, "LOCKED")
                    );
                })
                .toList();

        List<CashBackAccountByPlatformItem> byPlatform = accounts.stream()
                .filter(a -> StringUtils.hasText(a.getPlatformName()))
                .collect(Collectors.groupingBy(ToolAccount::getPlatformName))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .map(entry -> {
                    List<ToolAccount> group = entry.getValue();
                    return new CashBackAccountByPlatformItem(
                            entry.getKey(),
                            group.size(),
                            countByStatus(group, "RUNNING"),
                            countByStatus(group, "PAUSED"),
                            countByStatus(group, "LOCKED")
                    );
                })
                .toList();

        List<BalanceByUserNameItem> balanceByUserName = accounts.stream()
                .filter(a -> StringUtils.hasText(a.getUserName()))
                .collect(Collectors.groupingBy(ToolAccount::getUserName))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .map(entry -> new BalanceByUserNameItem(
                        entry.getKey(),
                        sumBalance(entry.getValue())
                ))
                .toList();

        List<BalanceByPlatformItem> balanceByPlatform = accounts.stream()
                .filter(a -> StringUtils.hasText(a.getPlatformName()))
                .collect(Collectors.groupingBy(ToolAccount::getPlatformName))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .map(entry -> new BalanceByPlatformItem(
                        entry.getKey(),
                        sumBalance(entry.getValue())
                ))
                .toList();

        return new DashboardCashBackAccountResponse(byUserName, byPlatform, balanceByUserName, balanceByPlatform);
    }

    private long countByStatus(List<ToolAccount> accounts, String status) {
        return accounts.stream()
                .filter(a -> status.equalsIgnoreCase(a.getStatus()))
                .count();
    }

    private BigDecimal sumBalance(List<ToolAccount> accounts) {
        return accounts.stream()
                .map(a -> a.getBalance() != null ? a.getBalance() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<IncomeExpenditureItem> getIncomeExpenditureDashboard(Long currentUserId) {
        List<ToolIncome> incomes = toolIncomeService.findAllForUser(currentUserId);
        List<ToolOutcome> outcomes = toolOutcomeService.findAllForUser(currentUserId);

        // Accumulate income per month, converting each record to USD
        Map<String, BigDecimal> incomeByMonth = new TreeMap<>();
        for (ToolIncome income : incomes) {
            if (income.getPayoutDate() != null && income.getIncomeAmount() != null) {
                String month = income.getPayoutDate().format(MONTH_FORMATTER);
                BigDecimal usdAmount = currencyExchangeService.toUsd(
                        income.getIncomeAmount(), income.getCurrency(), income.getPayoutDate());
                incomeByMonth.merge(month, usdAmount, BigDecimal::add);
            }
        }

        // Accumulate expenditure per month, converting each record to USD
        Map<String, BigDecimal> expenditureByMonth = new TreeMap<>();
        for (ToolOutcome outcome : outcomes) {
            if (outcome.getPayDate() != null && outcome.getOutcomeAmount() != null) {
                String month = outcome.getPayDate().format(MONTH_FORMATTER);
                BigDecimal usdAmount = currencyExchangeService.toUsd(
                        outcome.getOutcomeAmount(), outcome.getCurrency(), outcome.getPayDate());
                expenditureByMonth.merge(month, usdAmount, BigDecimal::add);
            }
        }

        // Merge all months from both maps, sorted ascending
        Map<String, BigDecimal[]> merged = new TreeMap<>();
        incomeByMonth.forEach((month, amount) ->
                merged.computeIfAbsent(month, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO})[0] = amount);
        expenditureByMonth.forEach((month, amount) ->
                merged.computeIfAbsent(month, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO})[1] = amount);

        return merged.entrySet().stream()
                .map(e -> new IncomeExpenditureItem(e.getKey(), e.getValue()[0], e.getValue()[1], TARGET_CURRENCY))
                .toList();
    }

    private List<ShiftLinkNormalDashboardItem> buildNormalDashboard(List<ShiftLink> shiftLinks) {
        return shiftLinks.stream()
                .filter(this::isNormal)
                .collect(Collectors.groupingBy(ShiftLink::getAdsName))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .map(entry -> {
                    List<ShiftLink> items = entry.getValue();
                    long total = items.stream().mapToLong(link -> normalizeDisplayNumber(link.getDisplayNumber())).sum();
                    long consumed = items.stream().mapToLong(link -> normalizeDisplayTimes(link.getDisplayTimes())).sum();
                    long remaining = Math.max(total - consumed, 0L);
                    String campaignName = appendSuffix(entry.getKey(), items.stream()
                            .map(ShiftLink::getPlatformName)
                            .filter(StringUtils::hasText)
                            .map(String::trim)
                            .collect(Collectors.toList()));
                    return new ShiftLinkNormalDashboardItem(campaignName, total, consumed, remaining);
                })
                .toList();
    }

    private List<ShiftLinkMatrixDashboardItem> buildMatrixDashboard(List<ShiftLink> shiftLinks) {
        return shiftLinks.stream()
                .filter(this::isMatrix)
                .collect(Collectors.groupingBy(ShiftLink::getAdsName))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .map(entry -> {
                    List<ShiftLink> items = entry.getValue();
                    long totalCapacity = items.stream().mapToLong(link -> normalizeDisplayNumber(link.getDisplayNumber())).sum();
                    long consumed = items.stream().mapToLong(link -> normalizeDisplayTimes(link.getDisplayTimes())).sum();
                    long remaining = Math.max(totalCapacity - consumed, 0L);
                    String campaignName = appendSuffix(entry.getKey(), items.stream()
                            .map(ShiftLink::getRemarks)
                            .filter(StringUtils::hasText)
                            .map(String::trim)
                            .distinct()
                            .toList());
                    return new ShiftLinkMatrixDashboardItem(campaignName, totalCapacity, consumed, remaining);
                })
                .toList();
    }

    private boolean isNormal(ShiftLink shiftLink) {
        return hasAdsType(shiftLink, "NORMAL");
    }

    private boolean isMatrix(ShiftLink shiftLink) {
        return hasAdsType(shiftLink, "MATRIX");
    }

    private boolean hasAdsType(ShiftLink shiftLink, String expected) {
        if (shiftLink == null || !StringUtils.hasText(shiftLink.getAdsType())) {
            return false;
        }
        return expected.equalsIgnoreCase(shiftLink.getAdsType().trim());
    }

    private long normalizeDisplayNumber(Long value) {
        return value == null ? 0L : value;
    }

    private long normalizeDisplayTimes(Long value) {
        return value == null ? 0L : value;
    }

    private String appendSuffix(String campaignName, List<String> suffixes) {
        if (!StringUtils.hasText(campaignName) || suffixes == null || suffixes.isEmpty()) {
            return campaignName;
        }
        List<String> distinctSuffixes = new java.util.ArrayList<>(new LinkedHashSet<>(suffixes));
        if (distinctSuffixes.isEmpty()) {
            return campaignName;
        }
        return campaignName + "(" + String.join("/", distinctSuffixes) + ")";
    }
}
