package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AdsUrl;
import com.admire.cars.runner.entity.AdsUrlAud;
import com.admire.cars.runner.repository.AdsUrlRepository;
import com.admire.cars.runner.repository.AdsUrlAudRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class AdsUrlService {

    private final AdsUrlRepository adsUrlRepository;
    private final AdsUrlAudRepository adsUrlAudRepository;

    public AdsUrlService(AdsUrlRepository adsUrlRepository, AdsUrlAudRepository adsUrlAudRepository) {
        this.adsUrlRepository = adsUrlRepository;
        this.adsUrlAudRepository = adsUrlAudRepository;
    }

    @Transactional
    public AdsUrl createAdsUrl(AdsUrl adsUrl) {
        if (adsUrl.getCapMainName() == null || adsUrl.getFullUrl() == null || adsUrl.getCampaignOwner() == null) {
            throw new IllegalArgumentException("capMainName, fullUrl, and campaignOwner are required");
        }
        AdsUrl saved = adsUrlRepository.save(adsUrl);
        createAuditEntry(saved, 1L, "INSERT");
        return saved;
    }

    public AdsUrl getAdsUrlById(Long id) {
        return adsUrlRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ADS_URL not found: " + id));
    }

    public List<AdsUrl> getAllAdsUrls() {
        return adsUrlRepository.findAll();
    }

    public List<AdsUrl> getAdsUrlsByCampaignOwner(Long campaignOwner) {
        return adsUrlRepository.findByCampaignOwner(campaignOwner);
    }

    public List<AdsUrl> getAdsUrlsByCapMainName(String capMainName) {
        return adsUrlRepository.findByCapMainName(capMainName);
    }

    public List<AdsUrl> getAdsUrlsByPlatform(String platform) {
        return adsUrlRepository.findByPlatform(platform);
    }

    @Transactional
    public AdsUrl updateAdsUrl(Long id, AdsUrl updateData) {
        AdsUrl existing = getAdsUrlById(id);
        
        if (updateData.getCapMainName() != null) existing.setCapMainName(updateData.getCapMainName());
        if (updateData.getFullUrl() != null) existing.setFullUrl(updateData.getFullUrl());
        if (updateData.getLandingUrl() != null) existing.setLandingUrl(updateData.getLandingUrl());
        if (updateData.getUrlSuffix() != null) existing.setUrlSuffix(updateData.getUrlSuffix());
        if (updateData.getPlatform() != null) existing.setPlatform(updateData.getPlatform());
        if (updateData.getRemark() != null) existing.setRemark(updateData.getRemark());
        if (updateData.getSeqNumber() != null) existing.setSeqNumber(updateData.getSeqNumber());
        if (updateData.getDisplayTimes() != null) existing.setDisplayTimes(updateData.getDisplayTimes());

        Long newVersion = (existing.getVersion() != null ? existing.getVersion() : 0L) + 1L;
        existing.setVersion(newVersion);
        AdsUrl updated = adsUrlRepository.save(existing);
        createAuditEntry(updated, newVersion, "UPDATE");
        return updated;
    }

    @Transactional
    public void deleteAdsUrl(Long id) {
        AdsUrl existing = getAdsUrlById(id);
        adsUrlRepository.delete(existing);
        Long delVersion = (existing.getVersion() != null ? existing.getVersion() : 0L) + 1L;
        createAuditEntry(existing, delVersion, "DELETE");
    }

    public List<AdsUrlAud> getAdsUrlAuditHistory(Long id) {
        return adsUrlAudRepository.findById(id);
    }

    @Transactional
    public void incrementDisplayTimes(Long id) {
        AdsUrl adsUrl = getAdsUrlById(id);
        adsUrl.setDisplayTimes((adsUrl.getDisplayTimes() != null ? adsUrl.getDisplayTimes() : 0L) + 1);
        adsUrlRepository.save(adsUrl);
    }

    private void createAuditEntry(AdsUrl adsUrl, Long reversion, String operation) {
        AdsUrlAud audit = new AdsUrlAud();
        audit.setId(adsUrl.getId());
        audit.setCapMainName(adsUrl.getCapMainName());
        audit.setFullUrl(adsUrl.getFullUrl());
        audit.setLandingUrl(adsUrl.getLandingUrl());
        audit.setUrlSuffix(adsUrl.getUrlSuffix());
        audit.setPlatform(adsUrl.getPlatform());
        audit.setRemark(adsUrl.getRemark());
        audit.setCampaignOwner(adsUrl.getCampaignOwner());
        audit.setSeqNumber(adsUrl.getSeqNumber());
        audit.setDisplayTimes(adsUrl.getDisplayTimes());
        audit.setCreateDate(adsUrl.getCreateDate());
        audit.setUpdateDate(adsUrl.getUpdateDate());
        audit.setVersion(adsUrl.getVersion());
        audit.setReversion(reversion);
        adsUrlAudRepository.save(audit);
    }
}
