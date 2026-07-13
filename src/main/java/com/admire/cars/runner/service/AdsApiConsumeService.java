package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.ShiftLink;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.ShiftLinkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AdsApiConsumeService {

    private static final String MATRIX_ADS_TYPE = "Matrix";
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

        int randomIndex = ThreadLocalRandom.current().nextInt(eligibleLinks.size());
        ShiftLink selectedLink = eligibleLinks.get(randomIndex);
        String shiftLink = selectedLink.getFullUrl().replace(selectedLink.getLandingPageUrl(),"{lpurl}");
        shiftLinkConsumeAsyncService.recordConsume(selectedLink.getId());

        return shiftLink;
    }

}
