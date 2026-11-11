package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.ShiftLink;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.ShiftLinkRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
public class AdsApiConsumeService {

    private static final String MATRIX_ADS_TYPE = "Matrix";
    private static final String NORMAL_ADS_TYPE = "Normal";
    private static final String RUNNING_STATUS = "RUNNING";
    // 无可用链时回退裸落地页占位符, 避免最后一条链被过度点击 / bare landing page when no link is available
    private static final String BARE_LANDING_TEMPLATE = "{lpurl}";

    private final UserService userService;

    private final ShiftLinkRepository shiftLinkRepository;

    private final ShiftLinkConsumeAsyncService shiftLinkConsumeAsyncService;

    public AdsApiConsumeService(
            UserService userService,
            ShiftLinkRepository shiftLinkRepository,
            ShiftLinkConsumeAsyncService shiftLinkConsumeAsyncService) {
        this.userService = userService;
        this.shiftLinkRepository = shiftLinkRepository;
        this.shiftLinkConsumeAsyncService = shiftLinkConsumeAsyncService;
    }

    @Transactional(readOnly = true)
    public String consumeMatrixAds(String campaignName, String apiKey) {
        if (!StringUtils.hasText(campaignName)) {
            throw new IllegalArgumentException("campaignName is required");
        }

        User user = userService.getEnabledUserByApiKey(apiKey);
        String normalizedCampaignName = campaignName.trim();
        String adsOwner = user.getUserPhoneNumber();
        List<ShiftLink> eligibleLinks = shiftLinkRepository.findEligibleForConsume(
                adsOwner,
                normalizedCampaignName,
                MATRIX_ADS_TYPE,
                RUNNING_STATUS);

        if (eligibleLinks.isEmpty()) {
            // 链全部耗尽时回退裸落地页, 不再复用最后一条链 / all links exhausted, fall back to bare landing page
            log.warn("no available matrix shift link campaign={} owner={}, fallback to bare lpurl",
                    normalizedCampaignName, adsOwner);
            return BARE_LANDING_TEMPLATE;
        }
//
//        // Step 1: Group by platformName -> Map<platformName, List<ShiftLink>>
//        Map<String, List<ShiftLink>> groupedByPlatform = eligibleLinks.stream()
//                .collect(Collectors.groupingBy(link ->
//                        link.getPlatformName() == null ? "" : link.getPlatformName()));
//
//        // Step 2: Find the group with the highest remaining display capacity
//        //         display cap = sum(displayNumber) - sum(displayTimes) for all links in group
//        List<ShiftLink> largestPlatformGroup = groupedByPlatform.values().stream()
//                .max(Comparator.comparingLong(group -> {
//                    long totalDisplayNumber = group.stream()
//                            .mapToLong(l -> l.getDisplayNumber() == null ? 0L : l.getDisplayNumber())
//                            .sum();
//                    long totalDisplayTimes = group.stream()
//                            .mapToLong(l -> l.getDisplayTimes() == null ? 0L : l.getDisplayTimes())
//                            .sum();
//                    return totalDisplayNumber - totalDisplayTimes;
//                }))
//                .orElse(eligibleLinks);
//
//        // Step 3: Sort the selected group by createDate DESC
//        largestPlatformGroup.sort(Comparator.comparing(ShiftLink::getCreateDate,
//                Comparator.nullsLast(Comparator.reverseOrder())));

        ShiftLink selectedLink = eligibleLinks.get(0);
        String shiftLink = buildTrackingTemplate(selectedLink);
        // Matrix 消耗由真实点击数驱动, 拉取模板不再累加 displayTimes / clicks drive consumption, not fetches
        shiftLinkConsumeAsyncService.recordLogOnly(selectedLink.getId());

        return shiftLink;
    }


    @Transactional(readOnly = true)
    public String consumeNormalAds(String campaignName, String apiKey) {
        if (!StringUtils.hasText(campaignName)) {
            throw new IllegalArgumentException("campaignName is required");
        }

        User user = userService.getEnabledUserByApiKey(apiKey);
        String normalizedCampaignName = campaignName.trim();
        String adsOwner = user.getUserPhoneNumber();

        List<ShiftLink> eligibleLinks = shiftLinkRepository.findEligibleForNormalConsume(
                adsOwner,
                normalizedCampaignName,
                NORMAL_ADS_TYPE,
                RUNNING_STATUS);
        if (eligibleLinks.isEmpty()) {
            log.warn("no available normal shift link campaign={} owner={}, fallback to bare lpurl",
                    normalizedCampaignName, adsOwner);
            return BARE_LANDING_TEMPLATE;
        }
        ShiftLink selectedLink = eligibleLinks.get(0);
        String shiftLink = buildTrackingTemplate(selectedLink);
        // Normal 同样由真实点击驱动, 拉取模板不再累加 displayTimes / normal consumption is click driven too
        shiftLinkConsumeAsyncService.recordLogOnly(selectedLink.getId());

        return shiftLink;
    }

    // 生成跟踪模板: landingPageUrl 必须逐字符等于 fullUrl 中的落地页片段, 否则替换后缺少 {lpurl}, Google 会拒绝
    // Build tracking template; only the first occurrence is replaced, and mismatches fail fast instead of returning a bad template
    private String buildTrackingTemplate(ShiftLink selectedLink) {
        String fullUrl = selectedLink.getFullUrl();
        String landingPageUrl = selectedLink.getLandingPageUrl();
        if (!StringUtils.hasText(landingPageUrl)) {
            throw new IllegalArgumentException(
                    "landingPageUrl is required for shift link id=" + selectedLink.getId());
        }
        int index = fullUrl.indexOf(landingPageUrl);
        if (index < 0) {
            throw new IllegalArgumentException(
                    "landingPageUrl does not match fullUrl for shift link id=" + selectedLink.getId()
                            + ", adsName=" + selectedLink.getAdsName());
        }
        return fullUrl.substring(0, index) + "{lpurl}" + fullUrl.substring(index + landingPageUrl.length());
    }

}
