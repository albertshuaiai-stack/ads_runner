package com.admire.cars.runner.service;

import com.admire.cars.runner.dto.bonusarrive.BonusArriveCampaignDto;
import com.admire.cars.runner.dto.bonusarrive.BonusArriveCampaignItemDto;
import com.admire.cars.runner.dto.bonusarrive.BonusArriveCampaignResponseDto;
import com.admire.cars.runner.entity.AffiliateAdsSync;
import com.admire.cars.runner.entity.AffiliateAdsSyncConfig;
import com.admire.cars.runner.entity.AffiliateAdsSyncTask;
import com.admire.cars.runner.repository.AffiliateAdsSyncConfigRepository;
import com.admire.cars.runner.repository.AffiliateAdsSyncRepository;
import com.admire.cars.runner.repository.AffiliateAdsSyncTaskRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.compress.utils.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AffiliateAdsSyncTaskAsyncService {

    private static final Logger log = LoggerFactory.getLogger(AffiliateAdsSyncTaskAsyncService.class);

    private final AffiliateAdsSyncTaskRepository affiliateAdsSyncTaskRepository;
    private final AffiliateAdsSyncConfigRepository affiliateAdsSyncConfigRepository;
    private final AffiliateAdsSyncRepository affiliateAdsSyncRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate;

    public AffiliateAdsSyncTaskAsyncService(
            AffiliateAdsSyncTaskRepository affiliateAdsSyncTaskRepository,
            AffiliateAdsSyncConfigRepository affiliateAdsSyncConfigRepository,
            AffiliateAdsSyncRepository affiliateAdsSyncRepository,
            RestTemplate restTemplate) {
        this.affiliateAdsSyncTaskRepository = affiliateAdsSyncTaskRepository;
        this.affiliateAdsSyncConfigRepository = affiliateAdsSyncConfigRepository;
        this.affiliateAdsSyncRepository = affiliateAdsSyncRepository;
        this.restTemplate = restTemplate;
    }

    @Async("adsAsyncExecutor")
    @Transactional
    public void syncAdsAsync(Long taskId) {
        syncAdsNow(taskId);
    }

    @Transactional
    public void syncAdsNow(Long taskId) {
        AffiliateAdsSyncTask task = affiliateAdsSyncTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("AFFILIATE_ADS_SYNC_TASK not found: " + taskId));
        AffiliateAdsSyncConfig config = affiliateAdsSyncConfigRepository.findById(task.getAffiliateAdsSyncConfigId())
                .orElseThrow(() -> new IllegalArgumentException("AFFILIATE_ADS_SYNC_CONFIG not found: " + task.getAffiliateAdsSyncConfigId()));

        try {
            String syncRegion = task.getRegion() == null ? null : task.getRegion().trim();
            long deleted = affiliateAdsSyncRepository.deleteByAffiliateNetworkAndAdsOwnerAndRegion(
                    config.getAffiliateNetwork(),
                    task.getAdsOwner(),
                    syncRegion);
            log.info("AFFILIATE_SYNC_TASK_CLEANUP taskId={} deleted={} affiliateNetwork={} adsOwner={} region={}",
                    taskId, deleted, config.getAffiliateNetwork(), task.getAdsOwner(), syncRegion);

            Long totalCount = 0L;
            Long successCount = 0L;
            Long failedCount =0L;

            Long pageNum = 0L;
            Long pageSize =100L;

            while(true) {
                BonusArriveCampaignDto bonusArriveCampaignDto = invokeAffiliateApi(config, pageNum, pageSize);
               if (bonusArriveCampaignDto == null || bonusArriveCampaignDto.getTotal_page() == null) {
                   throw new IllegalStateException("Affiliate API response is missing pagination metadata");
               }
               // Logic to handle multiple pages
               List<BonusArriveCampaignItemDto> bonusArriveCampaignItemDtos = bonusArriveCampaignDto.getItems();
               if (null != bonusArriveCampaignItemDtos && bonusArriveCampaignItemDtos.size() > 0) {
                   totalCount += bonusArriveCampaignItemDtos.size();
                   List<AffiliateAdsSync> syncRecords = Lists.newArrayList();
                   for (BonusArriveCampaignItemDto itemDto : bonusArriveCampaignItemDtos) {
                       if (isMatchingRegion(itemDto.getRegion(), syncRegion) && null != itemDto.getTracking_url()
                               && "active".equalsIgnoreCase(itemDto.getMerchant_status())) {
                           AffiliateAdsSync sync = new AffiliateAdsSync();
                           sync.setSiteName(truncate(itemDto.getSite_name(), 128));
                           sync.setSiteUrl(truncate(itemDto.getSite_url(), 1024));
                           sync.setSiteLogoUrl(truncate(itemDto.getSite_logo_url(), 1024));
                           sync.setTrackingUrl(truncate(itemDto.getTracking_url(), 1024));
                           sync.setRegion(truncate(itemDto.getRegion(), 512));
                           sync.setMerchantStatus(truncate(itemDto.getMerchant_status(), 128));
                           sync.setCommissions(truncate(formatCommissions(itemDto.getCommissions()), 512));
                           sync.setAdvCatagory(truncate(itemDto.getAdv_catagory(), 64));
                           sync.setDeeplink(truncate(itemDto.getDeeplink(), 64));
                           sync.setAffiliateNetwork(config.getAffiliateNetwork());
                           sync.setStatus("ENABLED");
                           sync.setAdsOwner(task.getAdsOwner());
                           sync.setCreateDate(LocalDateTime.now());
                           syncRecords.add(sync);
                       } else {
                           failedCount +=1;
                       }
                   }
                   successCount += syncRecords.size();
                   affiliateAdsSyncRepository.saveAll(syncRecords);
               }
               pageNum ++;
               if (pageNum >= bonusArriveCampaignDto.getTotal_page()) {
                   break;
               }
            }
            task.setTotalCount(totalCount);
            task.setSuccessCount(successCount);
            task.setFailedCount(failedCount);
            task.setPreEndDate(LocalDateTime.now());
            task.setPreDuration(calculateDurationSeconds(task.getPreStartDate(), task.getPreEndDate()));
            task.setStatus("COMPLETED");
            task.setUpdateDate(LocalDateTime.now());
            affiliateAdsSyncTaskRepository.save(task);
            log.info("AFFILIATE_SYNC_TASK_COMPLETED taskId={} total={}", taskId, totalCount);
        } catch (Exception e) {
            task.setPreEndDate(LocalDateTime.now());
            task.setPreDuration(calculateDurationSeconds(task.getPreStartDate(), task.getPreEndDate()));
            task.setStatus("FAILED");
            task.setFailedCount(task.getFailedCount() == null ? 1L : Math.max(1L, task.getFailedCount()));
            task.setUpdateDate(LocalDateTime.now());
            affiliateAdsSyncTaskRepository.save(task);
            log.error("AFFILIATE_SYNC_TASK_FAILED taskId={}: {}", taskId, e.getMessage(), e);
        }
    }


    private BonusArriveCampaignDto invokeAffiliateApi(AffiliateAdsSyncConfig config, Long pageNum, Long pageSize) {
        HttpMethod httpMethod = HttpMethod.valueOf(config.getMethod().toUpperCase());
        HttpHeaders headers = new HttpHeaders();
        applyHeaders(config.getRequestHeaders(), headers);
        HashMap<String, Object> payloadMap = new HashMap<>();
        payloadMap.put("per_page", pageSize);
        payloadMap.put("page", pageNum);
        HttpEntity<HashMap<String, Object>> requestEntity = new HttpEntity<>(payloadMap, headers);
        ResponseEntity<String> response = restTemplate.exchange(config.getUrl(), httpMethod, requestEntity, String.class);
        String responseBody = response.getBody();
        if (!StringUtils.hasText(responseBody)) {
            throw new IllegalStateException("Affiliate API returned an empty response body");
        }

        BonusArriveCampaignResponseDto responseDto = parseAffiliateResponse(responseBody);
        if (responseDto == null) {
            throw new IllegalStateException("Affiliate API response could not be parsed");
        }
        if (!"1".equalsIgnoreCase(String.valueOf(responseDto.getStatus()))) {
            throw new IllegalStateException("Affiliate API returned non-success status: " + responseDto.getStatus());
        }
        if (responseDto.getData() == null) {
            throw new IllegalStateException("Affiliate API response did not include campaign data");
        }
        return responseDto.getData();
    }

    private BonusArriveCampaignResponseDto parseAffiliateResponse(String responseBody) {
        String jsonPayload = extractJsonPayload(responseBody);
        try {
            return objectMapper.readValue(jsonPayload, BonusArriveCampaignResponseDto.class);
        } catch (Exception e) {
            throw new IllegalStateException("Affiliate API response is not valid campaign JSON", e);
        }
    }

    private String extractJsonPayload(String responseBody) {
        String trimmed = responseBody.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed;
        }

        int objectStart = trimmed.indexOf('{');
        int objectEnd = trimmed.lastIndexOf('}');
        if (objectStart >= 0 && objectEnd > objectStart) {
            return trimmed.substring(objectStart, objectEnd + 1);
        }

        int arrayStart = trimmed.indexOf('[');
        int arrayEnd = trimmed.lastIndexOf(']');
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            return trimmed.substring(arrayStart, arrayEnd + 1);
        }

        return trimmed;
    }

    private boolean isMatchingRegion(String itemRegion, String syncRegion) {
        if (!StringUtils.hasText(itemRegion)) {
            return false;
        }
        if (!StringUtils.hasText(syncRegion)) {
            return true;
        }
        return syncRegion.equals(itemRegion);
    }

    private Long calculateDurationSeconds(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return null;
        }
        return java.time.Duration.between(start, end).getSeconds();
    }

    private String formatCommissions(JsonNode commissionsNode) {
        if (commissionsNode == null || commissionsNode.isNull()) {
            return null;
        }
        if (commissionsNode.isTextual() || commissionsNode.isNumber() || commissionsNode.isBoolean()) {
            return commissionsNode.asText();
        }
        if (commissionsNode.isArray()) {
            return String.join(
                    ", ",
                    java.util.stream.StreamSupport.stream(commissionsNode.spliterator(), false)
                            .map(JsonNode::asText)
                            .collect(Collectors.toList()));
        }
        return commissionsNode.toString();
    }

    private void applyHeaders(String requestHeaders, HttpHeaders headers) {
        if (!StringUtils.hasText(requestHeaders)) {
            return;
        }
        try {
            JsonNode headerNode = objectMapper.readTree(requestHeaders);
            Iterator<Map.Entry<String, JsonNode>> fields = headerNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (field.getValue() != null && !field.getValue().isNull()) {
                    headers.set(field.getKey(), field.getValue().asText());
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("requestHeaders must be valid JSON object");
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
