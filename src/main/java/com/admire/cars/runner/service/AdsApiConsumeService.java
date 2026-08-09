package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AdsMatrixInfo;
import com.admire.cars.runner.entity.ShiftLink;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.ShiftLinkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class AdsApiConsumeService {

    private static final String MATRIX_ADS_TYPE = "Matrix";
    private static final String NORMAL_ADS_TYPE = "Normal";
    private static final String RUNNING_STATUS = "RUNNING";

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
            throw new IllegalArgumentException("No available SHIFT_LINK found for matrix ads");
        }

        // Step 1: Group by platformName -> Map<platformName, List<ShiftLink>>
        Map<String, List<ShiftLink>> groupedByPlatform = eligibleLinks.stream()
                .collect(Collectors.groupingBy(link ->
                        link.getPlatformName() == null ? "" : link.getPlatformName()));

        // Step 2: Find the group with the highest remaining display capacity
        //         display cap = sum(displayNumber) - sum(displayTimes) for all links in group
        List<ShiftLink> largestPlatformGroup = groupedByPlatform.values().stream()
                .max(Comparator.comparingLong(group -> {
                    long totalDisplayNumber = group.stream()
                            .mapToLong(l -> l.getDisplayNumber() == null ? 0L : l.getDisplayNumber())
                            .sum();
                    long totalDisplayTimes = group.stream()
                            .mapToLong(l -> l.getDisplayTimes() == null ? 0L : l.getDisplayTimes())
                            .sum();
                    return totalDisplayNumber - totalDisplayTimes;
                }))
                .orElse(eligibleLinks);

        // Step 3: Sort the selected group by createDate DESC
        largestPlatformGroup.sort(Comparator.comparing(ShiftLink::getCreateDate,
                Comparator.nullsLast(Comparator.reverseOrder())));

        ShiftLink selectedLink = largestPlatformGroup.get(0);
        String shiftLink = selectedLink.getFullUrl().replace(selectedLink.getLandingPageUrl(),"{lpurl}");
        shiftLinkConsumeAsyncService.recordConsume(selectedLink.getId());

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
            throw new IllegalArgumentException("No available SHIFT_LINK found for normal ads");
        }
        ShiftLink selectedLink = eligibleLinks.get(0);
        String shiftLink = selectedLink.getFullUrl().replace(selectedLink.getLandingPageUrl(),"{lpurl}");
        shiftLinkConsumeAsyncService.recordConsume(selectedLink.getId());

        return shiftLink;
    }

}
