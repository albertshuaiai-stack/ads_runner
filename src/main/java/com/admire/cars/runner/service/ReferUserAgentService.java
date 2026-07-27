package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.ReferUserAgent;
import com.admire.cars.runner.repository.ReferUserAgentRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Transactional
public class ReferUserAgentService {

    private final ReferUserAgentRepository referUserAgentRepository;
    private final Map<String, List<ReferUserAgent>> userAgentsByDeviceCache = new ConcurrentHashMap<>();

    public ReferUserAgentService(ReferUserAgentRepository referUserAgentRepository) {
        this.referUserAgentRepository = referUserAgentRepository;
    }

    @PostConstruct
    @Transactional(readOnly = true)
    public void loadCacheOnStartup() {
        reloadCache();
    }

    public ReferUserAgent create(ReferUserAgent referUserAgent) {
        validateAndNormalize(referUserAgent);
        ReferUserAgent created = referUserAgentRepository.save(referUserAgent);
        cacheDevice(created.getDevice());
        return created;
    }

    @Transactional(readOnly = true)
    public ReferUserAgent getById(Long id) {
        return referUserAgentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("REFER_USER_AGENT not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<ReferUserAgent> getAll() {
        return referUserAgentRepository.findAll();
    }

    public ReferUserAgent update(Long id, ReferUserAgent updateData) {
        if (updateData == null) {
            throw new IllegalArgumentException("updateData is required");
        }
        ReferUserAgent existing = getById(id);
        String previousDevice = existing.getDevice();

        if (updateData.getDevice() != null) {
            existing.setDevice(updateData.getDevice());
        }
        if (updateData.getUserAgent() != null) {
            existing.setUserAgent(updateData.getUserAgent());
        }

        validateAndNormalize(existing);
        ReferUserAgent updated = referUserAgentRepository.save(existing);

        cacheDevice(previousDevice);
        cacheDevice(updated.getDevice());
        return updated;
    }

    public void delete(Long id) {
        ReferUserAgent existing = getById(id);
        String previousDevice = existing.getDevice();
        referUserAgentRepository.delete(existing);
        cacheDevice(previousDevice);
    }

    @Transactional(readOnly = true)
    public List<String> getUserAgentListByDevice(String device) {
        String normalizedDevice = normalizeRequired(device, "device", 16);
        List<ReferUserAgent> cachedList = userAgentsByDeviceCache.get(normalizedDevice);
        if (cachedList == null) {
            cacheDevice(normalizedDevice);
            cachedList = userAgentsByDeviceCache.getOrDefault(normalizedDevice, Collections.emptyList());
        }
        return cachedList.stream().map(ReferUserAgent::getUserAgent).toList();
    }

    @Transactional(readOnly = true)
    public List<ReferUserAgent> getByDevice(String device) {
        String normalizedDevice = normalizeRequired(device, "device", 16);
        List<ReferUserAgent> cachedList = userAgentsByDeviceCache.get(normalizedDevice);
        if (cachedList == null) {
            cacheDevice(normalizedDevice);
            cachedList = userAgentsByDeviceCache.getOrDefault(normalizedDevice, Collections.emptyList());
        }
        return new ArrayList<>(cachedList);
    }

    private void reloadCache() {
        Map<String, List<ReferUserAgent>> grouped = referUserAgentRepository.findAll().stream()
                .collect(Collectors.groupingBy(item -> normalizeRequired(item.getDevice(), "device", 16), ConcurrentHashMap::new, Collectors.toList()));
        userAgentsByDeviceCache.clear();
        grouped.forEach((device, list) -> userAgentsByDeviceCache.put(device, List.copyOf(list)));
    }

    @Transactional(readOnly = true)
    protected void cacheDevice(String device) {
        String normalizedDevice = normalizeRequired(device, "device", 16);
        List<ReferUserAgent> latest = referUserAgentRepository.findByDeviceIgnoreCaseOrderByIdAsc(normalizedDevice);
        userAgentsByDeviceCache.put(normalizedDevice, List.copyOf(latest));
    }

    private void validateAndNormalize(ReferUserAgent referUserAgent) {
        if (referUserAgent == null) {
            throw new IllegalArgumentException("REFER_USER_AGENT is required");
        }
        referUserAgent.setDevice(normalizeRequired(referUserAgent.getDevice(), "device", 16));
        referUserAgent.setUserAgent(normalizeRequired(referUserAgent.getUserAgent(), "userAgent", 512));
    }

    private String normalizeRequired(String value, String fieldName, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must be at most " + maxLength + " characters");
        }
        if ("device".equals(fieldName)) {
            return normalized.toLowerCase(Locale.ROOT);
        }
        return normalized;
    }
}
