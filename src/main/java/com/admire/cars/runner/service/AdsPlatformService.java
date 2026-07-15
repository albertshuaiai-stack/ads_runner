package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AdsPlatform;
import com.admire.cars.runner.repository.AdsPlatformRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class AdsPlatformService {

    private final AdsPlatformRepository adsPlatformRepository;

    public AdsPlatformService(AdsPlatformRepository adsPlatformRepository) {
        this.adsPlatformRepository = adsPlatformRepository;
    }

    public AdsPlatform create(AdsPlatform adsPlatform) {
        validateRequiredFields(adsPlatform);
        if (adsPlatformRepository.existsByPlatformNameIgnoreCase(adsPlatform.getPlatformName())) {
            throw new IllegalArgumentException("platformName already exists");
        }
        return adsPlatformRepository.save(adsPlatform);
    }

    @Transactional(readOnly = true)
    public AdsPlatform getById(Long id) {
        return adsPlatformRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ADS_PLATFORM not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<AdsPlatform> getAll(Pageable pageable) {
        return adsPlatformRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<AdsPlatform> getAll() {
        return adsPlatformRepository.findAll(Sort.by(Sort.Direction.DESC, "id"));
    }

    public AdsPlatform update(Long id, AdsPlatform updateData) {
        AdsPlatform existing = getById(id);

        if (updateData.getPlatformName() != null) {
            existing.setPlatformName(updateData.getPlatformName());
        }
        if (updateData.getPaymentMethod() != null) {
            existing.setPaymentMethod(updateData.getPaymentMethod());
        }
        if (updateData.getRemarks() != null) {
            existing.setRemarks(updateData.getRemarks());
        }

        validateRequiredFields(existing);
        boolean duplicate = adsPlatformRepository.findByPlatformNameIgnoreCase(existing.getPlatformName())
                .filter(platform -> !platform.getId().equals(id))
                .isPresent();
        if (duplicate) {
            throw new IllegalArgumentException("platformName already exists");
        }
        return adsPlatformRepository.save(existing);
    }

    public void delete(Long id) {
        AdsPlatform existing = getById(id);
        adsPlatformRepository.delete(existing);
    }

    private void validateRequiredFields(AdsPlatform adsPlatform) {
        if (adsPlatform.getPlatformName() == null || adsPlatform.getPlatformName().isBlank()) {
            throw new IllegalArgumentException("platformName is required");
        }
        if (adsPlatform.getPlatformName().length() > 32) {
            throw new IllegalArgumentException("platformName must be at most 32 characters");
        }
        if (adsPlatform.getPaymentMethod() != null && adsPlatform.getPaymentMethod().length() > 64) {
            throw new IllegalArgumentException("paymentMethod must be at most 64 characters");
        }
        if (adsPlatform.getRemarks() != null && adsPlatform.getRemarks().length() > 256) {
            throw new IllegalArgumentException("remarks must be at most 256 characters");
        }
    }
}
