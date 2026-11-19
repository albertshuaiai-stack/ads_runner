package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AdsPlatform;
import com.admire.cars.runner.entity.GmailPlatformLink;
import com.admire.cars.runner.entity.ShiftLink;
import com.admire.cars.runner.entity.ToolEmail;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.AdsPlatformRepository;
import com.admire.cars.runner.repository.GmailPlatformLinkRepository;
import com.admire.cars.runner.repository.ShiftLinkRepository;
import com.admire.cars.runner.repository.ToolEmailRepository;
import com.admire.cars.runner.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 落地页导入: 文件夹结构 CashBack/邮箱名/返利平台/商家.txt, 每行一个落地页, 导入为 ShiftLink 并联动 Gmail Link Rule
 * Landing import: CashBack/gmail/platform/merchant.txt, one landing url per line, saved as shift links tied to gmail link rules
 * 不加事务: 每条 ShiftLink 各自提交, 单条失败(如重复)不回滚整批
 * not transactional on purpose: each shift link commits on its own so one failure never rolls back the batch
 */
@Slf4j
@Service
public class LandingImportService {

    public record Entry(String userName, String platformName, String merchant, List<String> urls) {
    }

    public record EntryResult(String userName, String platformName, String merchant,
                              int imported, int duplicated, int failed, String skippedReason) {
    }

    public record ImportResult(int imported, int duplicated, int failed, int skippedEntries,
                               List<String> createdRules, List<String> createdPlatforms, List<EntryResult> entries) {
    }

    private final ShiftLinkService shiftLinkService;
    private final GmailPlatformLinkService gmailPlatformLinkService;
    private final ShiftLinkRepository shiftLinkRepository;
    private final GmailPlatformLinkRepository gmailPlatformLinkRepository;
    private final ToolEmailRepository toolEmailRepository;
    private final AdsPlatformRepository adsPlatformRepository;
    private final UserRepository userRepository;

    public LandingImportService(ShiftLinkService shiftLinkService,
                                GmailPlatformLinkService gmailPlatformLinkService,
                                ShiftLinkRepository shiftLinkRepository,
                                GmailPlatformLinkRepository gmailPlatformLinkRepository,
                                ToolEmailRepository toolEmailRepository,
                                AdsPlatformRepository adsPlatformRepository,
                                UserRepository userRepository) {
        this.shiftLinkService = shiftLinkService;
        this.gmailPlatformLinkService = gmailPlatformLinkService;
        this.shiftLinkRepository = shiftLinkRepository;
        this.gmailPlatformLinkRepository = gmailPlatformLinkRepository;
        this.toolEmailRepository = toolEmailRepository;
        this.adsPlatformRepository = adsPlatformRepository;
        this.userRepository = userRepository;
    }

    public ImportResult importEntries(Long currentUserId, String adsType, Long displayNumber, List<Entry> entries) {
        User owner = userRepository.findById(currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found: " + currentUserId));
        if (!StringUtils.hasText(adsType)) {
            throw new IllegalArgumentException("adsType is required");
        }
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("entries are required");
        }
        String adsOwner = owner.getUserPhoneNumber();
        List<String> createdRules = new ArrayList<>();
        List<String> createdPlatforms = new ArrayList<>();
        List<EntryResult> results = new ArrayList<>();
        int imported = 0;
        int duplicated = 0;
        int failed = 0;
        int skippedEntries = 0;

        for (Entry entry : entries) {
            String merchant = trimToNull(entry.merchant());
            String platformInput = trimToNull(entry.platformName());
            ToolEmail email = resolveEmail(entry.userName());
            if (email == null || merchant == null || platformInput == null) {
                String reason = email == null ? "gmail not found in email management: " + entry.userName()
                        : "platform and merchant are required";
                results.add(new EntryResult(entry.userName(), platformInput, merchant, 0, 0, 0, reason));
                skippedEntries++;
                log.warn("LANDING_IMPORT_SKIP owner={} gmail={} platform={} merchant={} reason={}",
                        adsOwner, entry.userName(), platformInput, merchant, reason);
                continue;
            }
            String platformName = ensurePlatform(platformInput, createdPlatforms);
            boolean ruleExisted = !gmailPlatformLinkRepository
                    .findByAdsOwnerAndUserNameIgnoreCaseAndPlatformNameIgnoreCase(adsOwner, email.getUserName(), platformName)
                    .isEmpty();
            GmailPlatformLink rule = gmailPlatformLinkService.ensureRule(adsOwner, email.getUserName(), platformName);
            if (!ruleExisted) {
                createdRules.add(email.getUserName() + "/" + platformName + " (" + rule.getMatchId() + ")");
                log.info("LANDING_IMPORT_RULE_CREATED owner={} gmail={} platform={} matchId={}",
                        adsOwner, email.getUserName(), platformName, rule.getMatchId());
            }
            // 平台已停用的账号不入库, 统计里按被封处理 / disabled accounts are skipped, stats treat them as banned
            if (GmailPlatformLink.STATUS_DISABLED.equalsIgnoreCase(rule.getStatus())) {
                results.add(new EntryResult(email.getUserName(), platformName, merchant, 0, 0, 0, "gmail link rule is DISABLED"));
                skippedEntries++;
                log.info("LANDING_IMPORT_SKIP_DISABLED owner={} gmail={} platform={} merchant={} urls={}",
                        adsOwner, email.getUserName(), platformName, merchant, entry.urls() == null ? 0 : entry.urls().size());
                continue;
            }

            int entryImported = 0;
            int entryDuplicated = 0;
            int entryFailed = 0;
            for (String rawUrl : entry.urls() == null ? List.<String>of() : entry.urls()) {
                String fullUrl = trimToNull(rawUrl);
                if (fullUrl == null) {
                    continue;
                }
                if (!shiftLinkRepository.findByAdsOwnerAndFullUrl(adsOwner, fullUrl).isEmpty()) {
                    entryDuplicated++;
                    continue;
                }
                try {
                    shiftLinkService.createShiftLink(toShiftLink(adsOwner, email.getUserName(), platformName,
                            merchant, adsType, displayNumber, fullUrl), currentUserId);
                    entryImported++;
                } catch (Exception e) {
                    entryFailed++;
                    log.warn("LANDING_IMPORT_LINK_FAILED owner={} gmail={} platform={} merchant={} error={}",
                            adsOwner, email.getUserName(), platformName, merchant, e.getMessage());
                }
            }
            imported += entryImported;
            duplicated += entryDuplicated;
            failed += entryFailed;
            results.add(new EntryResult(email.getUserName(), platformName, merchant,
                    entryImported, entryDuplicated, entryFailed, null));
        }
        log.info("LANDING_IMPORT_DONE owner={} entries={} imported={} duplicated={} failed={} skippedEntries={} createdRules={} createdPlatforms={}",
                adsOwner, entries.size(), imported, duplicated, failed, skippedEntries, createdRules.size(), createdPlatforms.size());
        return new ImportResult(imported, duplicated, failed, skippedEntries, createdRules, createdPlatforms, results);
    }

    // 文件夹名可以是邮箱 userName 或完整邮箱地址 / the folder name may be the gmail userName or the full address
    private ToolEmail resolveEmail(String value) {
        String key = trimToNull(value);
        if (key == null) {
            return null;
        }
        return toolEmailRepository.findByUserName(key)
                .or(() -> toolEmailRepository.findByEmailAddress(key))
                .orElse(null);
    }

    private String ensurePlatform(String platformName, List<String> createdPlatforms) {
        return adsPlatformRepository.findByPlatformNameIgnoreCase(platformName)
                .map(AdsPlatform::getPlatformName)
                .orElseGet(() -> {
                    AdsPlatform platform = new AdsPlatform();
                    platform.setPlatformName(platformName);
                    adsPlatformRepository.save(platform);
                    createdPlatforms.add(platformName);
                    log.info("LANDING_IMPORT_PLATFORM_CREATED platform={}", platformName);
                    return platformName;
                });
    }

    private static ShiftLink toShiftLink(String adsOwner, String userName, String platformName, String merchant,
                                         String adsType, Long displayNumber, String fullUrl) {
        ShiftLink link = new ShiftLink();
        link.setAdsOwner(adsOwner);
        link.setUserName(userName);
        link.setPlatformName(platformName);
        link.setAdsName(merchant);
        link.setAdsType(adsType);
        link.setDisplayNumber(displayNumber);
        link.setFullUrl(fullUrl);
        // 与前端 parseAdsUrl 一致: 落地页 = origin + path, 后缀 = query + hash / same split as the front's parseAdsUrl
        try {
            URI uri = new URI(fullUrl);
            String path = uri.getRawPath() == null ? "" : uri.getRawPath();
            link.setLandingPageUrl(uri.getScheme() + "://" + uri.getRawAuthority() + path);
            String suffix = (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery())
                    + (uri.getRawFragment() == null ? "" : "#" + uri.getRawFragment());
            link.setUrlSuffix(suffix.isEmpty() ? null : suffix);
        } catch (Exception e) {
            link.setLandingPageUrl(fullUrl);
        }
        return link;
    }

    // 落地页采集工具用: 返回邮箱、环境编号、平台密码、2FA 网址和该邮箱各平台规则状态
    // for the landing collector: gmail, profile serial, platform password, 2FA url and rule status per platform
    public List<Map<String, Object>> collectorAccounts(Long currentUserId, List<String> userNames) {
        User owner = userRepository.findById(currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found: " + currentUserId));
        List<String> wanted = userNames == null ? List.of()
                : userNames.stream().map(LandingImportService::trimToNull).filter(v -> v != null)
                .map(v -> v.toLowerCase(Locale.ROOT)).toList();
        List<Map<String, Object>> accounts = new ArrayList<>();
        for (ToolEmail email : toolEmailRepository.findAllByAdsOwnerOrderByCreateDateDescIdDesc(owner.getUserPhoneNumber())) {
            if (!wanted.isEmpty() && !wanted.contains(String.valueOf(email.getUserName()).toLowerCase(Locale.ROOT))) {
                continue;
            }
            List<Map<String, Object>> rules = new ArrayList<>();
            for (GmailPlatformLink rule : gmailPlatformLinkRepository
                    .findByAdsOwnerAndUserName(owner.getUserPhoneNumber(), email.getUserName())) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("platformName", rule.getPlatformName());
                r.put("status", rule.getStatus());
                r.put("matchId", rule.getMatchId());
                rules.add(r);
            }
            Map<String, Object> account = new LinkedHashMap<>();
            account.put("userName", email.getUserName());
            account.put("emailAddress", email.getEmailAddress());
            account.put("envSerial", email.getEnvSerial());
            account.put("platformPwd", email.getPlatformPwd());
            account.put("twoFaUrl", email.getTwoFaUrl());
            account.put("rules", rules);
            accounts.add(account);
        }
        log.info("COLLECTOR_ACCOUNTS owner={} requested={} returned={}", owner.getUserPhoneNumber(), wanted.size(), accounts.size());
        return accounts;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
