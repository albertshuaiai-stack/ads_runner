package com.admire.cars.runner.service;

import com.admire.cars.runner.dto.dashboard.DashboardShiftLinkResponse;
import com.admire.cars.runner.dto.dashboard.ShiftLinkMatrixDashboardItem;
import com.admire.cars.runner.dto.dashboard.ShiftLinkNormalDashboardItem;
import com.admire.cars.runner.entity.ShiftLink;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final ShiftLinkService shiftLinkService;

    public DashboardService(ShiftLinkService shiftLinkService) {
        this.shiftLinkService = shiftLinkService;
    }

    public DashboardShiftLinkResponse getShiftLinkDashboard(Long currentUserId) {
        List<ShiftLink> shiftLinks = shiftLinkService.getAllShiftLinks(currentUserId);

        List<ShiftLinkNormalDashboardItem> normal = buildNormalDashboard(shiftLinks);
        List<ShiftLinkMatrixDashboardItem> matrix = buildMatrixDashboard(shiftLinks);

        return new DashboardShiftLinkResponse(normal, matrix);
    }

    private List<ShiftLinkNormalDashboardItem> buildNormalDashboard(List<ShiftLink> shiftLinks) {
        return shiftLinks.stream()
                .filter(this::isNormal)
                .collect(Collectors.groupingBy(ShiftLink::getAdsName))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .map(entry -> {
                    List<ShiftLink> items = entry.getValue();
                    long total = items.size();
                    long consumed = items.stream().filter(link -> normalizeDisplayNumber(link.getDisplayNumber()) > 0).count();
                    long remaining = total - consumed;
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
