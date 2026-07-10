package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.ShiftLink;
import com.admire.cars.runner.entity.ShiftLinkAud;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.ShiftLinkAudRepository;
import com.admire.cars.runner.repository.ShiftLinkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

@Service
public class AdsApiConsumeService {

    private static final String MATRIX_ADS_TYPE = "Matrix";
    private static final String RUNNING_STATUS = "RUNNING";

    private final UserService userService;
    private final ShiftLinkRepository shiftLinkRepository;
    private final ShiftLinkAudRepository shiftLinkAudRepository;
    private final ShiftLinkConsumeAsyncService shiftLinkConsumeAsyncService;

    public AdsApiConsumeService(
            UserService userService,
            ShiftLinkRepository shiftLinkRepository,
            ShiftLinkAudRepository shiftLinkAudRepository,
            ShiftLinkConsumeAsyncService shiftLinkConsumeAsyncService) {
        this.userService = userService;
        this.shiftLinkRepository = shiftLinkRepository;
        this.shiftLinkAudRepository = shiftLinkAudRepository;
        this.shiftLinkConsumeAsyncService = shiftLinkConsumeAsyncService;
    }

    @Transactional(readOnly = true)
    public MatrixConsumeResult consumeMatrixAds(String campainName, String apiKey) {
        if (!StringUtils.hasText(campainName)) {
            throw new IllegalArgumentException("campain_name is required");
        }

        User user = userService.getEnabledUserByApiKey(apiKey);
        String normalizedCampainName = campainName.trim();
        String adsOwner = user.getUserPhoneNumber();

        List<ShiftLink> eligibleLinks = shiftLinkRepository.findEligibleForConsume(
                adsOwner,
                normalizedCampainName,
                MATRIX_ADS_TYPE,
                RUNNING_STATUS);
        if (eligibleLinks.isEmpty()) {
            throw new IllegalArgumentException("No available SHIFT_LINK found for matrix ads");
        }

        Long lastConsumedSeq = shiftLinkAudRepository
                .findFirstByAdsOwnerAndAdsNameAndAdsTypeOrderByOperationDateDesc(adsOwner, normalizedCampainName, MATRIX_ADS_TYPE)
                .map(ShiftLinkAud::getSeqNumber)
                .orElse(null);

        Long nextSeq = selectNextSeqNumber(eligibleLinks, lastConsumedSeq);
        ShiftLink selectedLink = eligibleLinks.stream()
                .filter(link -> Objects.equals(link.getSeqNumber(), nextSeq))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No SHIFT_LINK found for sequence number: " + nextSeq));

        shiftLinkConsumeAsyncService.recordConsume(selectedLink.getId());

        return new MatrixConsumeResult(
                normalizedCampainName,
                adsOwner,
                selectedLink.getSeqNumber(),
                selectedLink.getFullUrl());
    }

    private Long selectNextSeqNumber(List<ShiftLink> eligibleLinks, Long lastConsumedSeq) {
        List<Long> sequenceNumbers = eligibleLinks.stream()
                .map(ShiftLink::getSeqNumber)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        if (sequenceNumbers.isEmpty()) {
            throw new IllegalArgumentException("No sequence number found for available SHIFT_LINK");
        }
        if (lastConsumedSeq == null) {
            return sequenceNumbers.get(0);
        }
        for (Long sequenceNumber : sequenceNumbers) {
            if (sequenceNumber > lastConsumedSeq) {
                return sequenceNumber;
            }
        }
        return sequenceNumbers.get(0);
    }

    public record MatrixConsumeResult(
            String campainName,
            String adsOwner,
            Long seqNumber,
            String fullUrl) {
    }
}
