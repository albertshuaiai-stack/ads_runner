package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.ShiftLinkAud;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.ShiftLinkAudRepository;
import com.admire.cars.runner.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class ShiftLinkAudService {

    private final ShiftLinkAudRepository shiftLinkAudRepository;
    private final UserRepository userRepository;

    public ShiftLinkAudService(ShiftLinkAudRepository shiftLinkAudRepository, UserRepository userRepository) {
        this.shiftLinkAudRepository = shiftLinkAudRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<ShiftLinkAud> searchShiftLinkAudits(
            String adsType,
            String platformName,
            String campainName,
            Long currentUserId,
            Pageable pageable) {
        User currentUser = getCurrentUser(currentUserId);
        String scopedAdsOwner = isAdmin(currentUser) ? null : currentUser.getUserPhoneNumber();
        return shiftLinkAudRepository.searchAudits(
                normalizeFilter(adsType),
                normalizeFilter(platformName),
                normalizeFilter(campainName),
                scopedAdsOwner,
                pageable);
    }

    private User getCurrentUser(Long currentUserId) {
        return userRepository.findById(currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found: " + currentUserId));
    }

    private boolean isAdmin(User user) {
        return user.getUserRole() != null
                && java.util.Arrays.stream(user.getUserRole().split(","))
                .map(String::trim)
                .anyMatch(role -> "admin".equalsIgnoreCase(role));
    }

    private String normalizeFilter(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
