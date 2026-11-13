package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.ShiftLink;
import com.admire.cars.runner.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class AdsApiConsumeService {

    private static final String MATRIX_ADS_TYPE = "Matrix";
    private static final String NORMAL_ADS_TYPE = "Normal";
    // 无可用链时回退裸落地页占位符, 避免最后一条链被过度点击 / bare landing page when no link is available
    private static final String BARE_LANDING_TEMPLATE = "{lpurl}";

    private final UserService userService;

    private final ShiftLinkConsumeAsyncService shiftLinkConsumeAsyncService;

    private final ShiftLinkRotationHelper rotationHelper;

    public AdsApiConsumeService(
            UserService userService,
            ShiftLinkConsumeAsyncService shiftLinkConsumeAsyncService,
            ShiftLinkRotationHelper rotationHelper) {
        this.userService = userService;
        this.shiftLinkConsumeAsyncService = shiftLinkConsumeAsyncService;
        this.rotationHelper = rotationHelper;
    }

    @Transactional(readOnly = true)
    public String consumeMatrixAds(String campaignName, String apiKey) {
        return consume(campaignName, apiKey, MATRIX_ADS_TYPE);
    }

    @Transactional(readOnly = true)
    public String consumeNormalAds(String campaignName, String apiKey) {
        return consume(campaignName, apiKey, NORMAL_ADS_TYPE);
    }

    // 两种类型同一套规则: 取当前在用的链生成模板; 拉取不消耗点击, 消耗只由点击上报推进
    // Same rule for both types; fetching never consumes, only click reports move rotation forward
    private String consume(String campaignName, String apiKey, String adsType) {
        if (!StringUtils.hasText(campaignName)) {
            throw new IllegalArgumentException("campaignName is required");
        }

        User user = userService.getEnabledUserByApiKey(apiKey);
        String normalizedCampaignName = campaignName.trim();
        String adsOwner = user.getUserPhoneNumber();

        List<ShiftLink> links = rotationHelper.loadPool(adsOwner, normalizedCampaignName, adsType);
        Optional<ShiftLink> selected = rotationHelper.selectForTemplate(links, rotationHelper.capacity(adsType));
        if (selected.isEmpty()) {
            // 链全部用完时回退裸落地页, 不再复用最后一条链 / all links exhausted, fall back to bare landing page
            log.warn("no available shift link type={} campaign={} owner={}, fallback to bare lpurl",
                    adsType, normalizedCampaignName, adsOwner);
            return BARE_LANDING_TEMPLATE;
        }

        ShiftLink selectedLink = selected.get();
        String shiftLink = buildTrackingTemplate(selectedLink);
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
