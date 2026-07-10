package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.ShiftLink;
import com.admire.cars.runner.entity.ShiftLinkAud;
import com.admire.cars.runner.entity.AdsPlatform;
import com.admire.cars.runner.entity.AdsNormalInfo;
import com.admire.cars.runner.entity.AdsMatrixInfo;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.ShiftLinkRepository;
import com.admire.cars.runner.repository.ShiftLinkAudRepository;
import com.admire.cars.runner.repository.AdsPlatformRepository;
import com.admire.cars.runner.repository.AdsNormalInfoRepository;
import com.admire.cars.runner.repository.AdsMatrixInfoRepository;
import com.admire.cars.runner.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
public class ShiftLinkService {

    private final ShiftLinkRepository shiftLinkRepository;
    private final ShiftLinkAudRepository shiftLinkAudRepository;
    private final UserRepository userRepository;
    private final AdsPlatformRepository adsPlatformRepository;
    private final AdsNormalInfoRepository adsNormalInfoRepository;
    private final AdsMatrixInfoRepository adsMatrixInfoRepository;

    public ShiftLinkService(
            ShiftLinkRepository shiftLinkRepository,
            ShiftLinkAudRepository shiftLinkAudRepository,
            UserRepository userRepository,
            AdsPlatformRepository adsPlatformRepository,
            AdsNormalInfoRepository adsNormalInfoRepository,
            AdsMatrixInfoRepository adsMatrixInfoRepository) {
        this.shiftLinkRepository = shiftLinkRepository;
        this.shiftLinkAudRepository = shiftLinkAudRepository;
        this.userRepository = userRepository;
        this.adsPlatformRepository = adsPlatformRepository;
        this.adsNormalInfoRepository = adsNormalInfoRepository;
        this.adsMatrixInfoRepository = adsMatrixInfoRepository;
    }

    public ShiftLink createShiftLink(ShiftLink shiftLink, Long currentUserId) {
        prepareForSave(shiftLink, currentUserId);
        ShiftLink saved = shiftLinkRepository.save(shiftLink);
        createAuditEntry(saved, "INSERT");
        return saved;
    }

    @Transactional(readOnly = true)
    public ShiftLink getShiftLinkById(Long id) {
        return shiftLinkRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("SHIFT_LINK not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<ShiftLink> getAllShiftLinks(Long currentUserId) {
        User currentUser = getCurrentUser(currentUserId);
        if (isAdmin(currentUser)) {
            return shiftLinkRepository.findAll();
        }
        return shiftLinkRepository.findByAdsOwner(currentUser.getUserPhoneNumber());
    }

    @Transactional(readOnly = true)
    public Page<ShiftLink> searchShiftLinks(String platformName, String status, String adsOwner, Long currentUserId, Pageable pageable) {
        User currentUser = getCurrentUser(currentUserId);
        boolean admin = isAdmin(currentUser);
        String scopedAdsOwner = admin ? adsOwner : currentUser.getUserPhoneNumber();
        Specification<ShiftLink> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(platformName)) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("platformName")),
                        "%" + platformName.toLowerCase() + "%"));
            }

            if (StringUtils.hasText(status)) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("status")),
                        status.toLowerCase()));
            }

            if (StringUtils.hasText(scopedAdsOwner)) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("adsOwner")),
                        "%" + scopedAdsOwner.toLowerCase() + "%"));
            }

            return predicates.isEmpty()
                    ? criteriaBuilder.conjunction()
                    : criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        return shiftLinkRepository.findAll(specification, pageable);
    }

    public ShiftLink updateShiftLink(Long id, ShiftLink updateData, Long currentUserId) {
        ShiftLink existing = getShiftLinkById(id);

        // Validate ownership
        if (currentUserId != null) {
            User user = userRepository.findById(currentUserId)
                    .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found: " + currentUserId));
            if (!existing.getAdsOwner().equals(user.getUserPhoneNumber())) {
                throw new IllegalArgumentException("Unauthorized: you can only update your own links");
            }
        }

        mergeForUpdate(existing, updateData, currentUserId);
        ShiftLink updated = shiftLinkRepository.save(existing);
        createAuditEntry(updated, "UPDATE");
        return updated;
    }

    public void deleteShiftLink(Long id, Long currentUserId) {
        ShiftLink existing = getShiftLinkById(id);

        // Validate ownership
        if (currentUserId != null) {
            User user = userRepository.findById(currentUserId)
                    .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found: " + currentUserId));
            if (!existing.getAdsOwner().equals(user.getUserPhoneNumber())) {
                throw new IllegalArgumentException("Unauthorized: you can only delete your own links");
            }
        }

        shiftLinkRepository.delete(existing);
        createAuditEntry(existing, "DELETE");
    }

    @Transactional(readOnly = true)
    public List<ShiftLinkAud> getShiftLinkAuditHistory(Long shiftLinkId) {
        return shiftLinkAudRepository.findByShiftLinkIdOrderByOperationDateDesc(shiftLinkId);
    }

    public void incrementDisplayTimes(Long id) {
        ShiftLink shiftLink = getShiftLinkById(id);
        shiftLink.setDisplayTimes((shiftLink.getDisplayTimes() != null ? shiftLink.getDisplayTimes() : 0L) + 1L);
        shiftLinkRepository.save(shiftLink);
        createAuditEntry(shiftLink, "UPDATE");
    }

    public BulkUploadResult bulkUploadByExcel(MultipartFile file, Long currentUserId) {
        if (currentUserId == null) {
            throw new IllegalArgumentException("currentUserId is required");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Excel file is required");
        }

        List<ExcelRowData> rows = parseExcelRows(file);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Excel has no data rows");
        }

        User owner = userRepository.findById(currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found: " + currentUserId));

        int insertedCount = 0;
        String adsOwner = owner.getUserPhoneNumber();
        Set<String> replacedScopes = new HashSet<>();
        Map<String, Long> seqByScope = new HashMap<>();

        for (ExcelRowData row : rows) {
            String normalizedAdsType = normalizeAdsTypeValue(row.adsType());
            String scopeKey = buildScopeKey(adsOwner, row.adsName(), normalizedAdsType);
            if (replacedScopes.add(scopeKey)) {
                deleteShiftLinksByScope(adsOwner, row.adsName(), normalizedAdsType);
                seqByScope.put(scopeKey, 0L);
            }

            ShiftLink shiftLink = new ShiftLink();
            shiftLink.setAdsType(normalizedAdsType);
            shiftLink.setAdsName(row.adsName());
            shiftLink.setPlatformName(row.platformName());
            shiftLink.setFullUrl(row.fullUrl());
            shiftLink.setLandingPageUrl(row.landingPageUrl());
            shiftLink.setDisplayNumber(row.displayNumber());
            shiftLink.setDisplayTimes(row.displayNumber());
            shiftLink.setRemarks(row.remarks());
            shiftLink.setStatus("RUNNING");
            shiftLink.setAdsOwner(adsOwner);
            long nextSeq = seqByScope.compute(scopeKey, (key, current) -> current == null ? 1L : current + 1L);
            shiftLink.setSeqNumber(nextSeq);

            createShiftLink(shiftLink, currentUserId);
            insertedCount++;
        }

        return new BulkUploadResult(rows.size(), insertedCount);
    }

    public byte[] createTemplateWorkbook() {
        try (var inputStream = new ClassPathResource("template/Shift_Link_Temp.xlsx").getInputStream()) {
            return inputStream.readAllBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to create template", e);
        }
    }

    private List<ExcelRowData> parseExcelRows(MultipartFile file) {
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("Excel file has no sheets");
            }
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new IllegalArgumentException("Excel header row is missing");
            }

            Map<String, Integer> headerIndexes = new LinkedHashMap<>();
            for (int i = headerRow.getFirstCellNum(); i < headerRow.getLastCellNum(); i++) {
                String header = formatter.formatCellValue(headerRow.getCell(i));
                if (header != null && !header.isBlank()) {
                    headerIndexes.put(normalizeHeader(header), i);
                }
            }

            int headerCount = Math.max(headerRow.getLastCellNum(), 0);
            boolean detectedAdsTypeColumn = resolveHeaderIndex(headerIndexes, null, "ads_type") != null
                    || looksLikeAdsTypeInFirstDataColumn(sheet, formatter);
            boolean detectedLandingPageColumn = resolveHeaderIndex(headerIndexes, null, "landing_page_url") != null;
            if (!detectedLandingPageColumn) {
                detectedLandingPageColumn = looksLikeLandingPageColumn(sheet, formatter, detectedAdsTypeColumn, headerCount);
            }

            Integer adsTypeIndex = detectedAdsTypeColumn
                    ? findOptionalHeaderIndex(headerIndexes, 0, "ads_type")
                    : null;
            Integer adsNameIndex = findHeaderIndex(headerIndexes, 0, "ads_name", "campaign_name", "campain_name");
            Integer platformIndex = findHeaderIndex(headerIndexes, 1, "platform_name", "platform");
            Integer fullUrlIndex = findHeaderIndex(headerIndexes, 2, "full_url");
            Integer landingPageIndex = detectedLandingPageColumn
                    ? findOptionalHeaderIndex(headerIndexes, 3, "landing_page_url")
                    : null;
            Integer displayNumberIndex = findHeaderIndex(headerIndexes, detectedLandingPageColumn ? 4 : 3, "dsplay_number", "display_number");
            Integer remarksIndex = findOptionalHeaderIndex(headerIndexes, headerCount > (detectedLandingPageColumn ? 4 : 3) ? (detectedLandingPageColumn ? 5 : 4) : null, "remarks", "remark");

            if (detectedAdsTypeColumn) {
                adsNameIndex = shiftIndexWhenFallback(adsNameIndex, 0, 1);
                platformIndex = shiftIndexWhenFallback(platformIndex, 1, 2);
                fullUrlIndex = shiftIndexWhenFallback(fullUrlIndex, 2, 3);
                if (detectedLandingPageColumn) {
                    landingPageIndex = shiftIndexWhenFallback(landingPageIndex, 3, 4);
                    displayNumberIndex = shiftIndexWhenFallback(displayNumberIndex, 4, 5);
                    remarksIndex = shiftIndexWhenFallback(remarksIndex, 5, 6);
                } else {
                    displayNumberIndex = shiftIndexWhenFallback(displayNumberIndex, 3, 4);
                    remarksIndex = shiftIndexWhenFallback(remarksIndex, 4, 5);
                }
            }

            List<ExcelRowData> rows = new ArrayList<>();
            for (int rowNum = sheet.getFirstRowNum() + 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null) {
                    continue;
                }

                String adsType = readCell(row, adsTypeIndex, formatter);
                String adsName = readCell(row, adsNameIndex, formatter);
                String platformName = readCell(row, platformIndex, formatter);
                String fullUrl = readCell(row, fullUrlIndex, formatter);
                String landingPageUrl = readCell(row, landingPageIndex, formatter);
                String displayNumber = readCell(row, displayNumberIndex, formatter);
                String remarks = readCell(row, remarksIndex, formatter);

                boolean hasAdsTypeColumn = adsTypeIndex != null;
                if ((!hasAdsTypeColumn || adsType == null || adsType.isBlank())
                        && (adsName == null || adsName.isBlank())
                        && (platformName == null || platformName.isBlank())
                        && (fullUrl == null || fullUrl.isBlank())
                        && (displayNumber == null || displayNumber.isBlank())
                        && (remarks == null || remarks.isBlank())) {
                    continue;
                }

                if (hasAdsTypeColumn && !StringUtils.hasText(adsType)) {
                    throw new IllegalArgumentException("Ads_Type is required at row " + (rowNum + 1));
                }
                if (!StringUtils.hasText(adsName)) {
                    throw new IllegalArgumentException("Ads_Name is required at row " + (rowNum + 1));
                }
                if (!StringUtils.hasText(platformName)) {
                    throw new IllegalArgumentException("Platform_Name is required at row " + (rowNum + 1));
                }
                if (!StringUtils.hasText(fullUrl)) {
                    throw new IllegalArgumentException("Full_URL is required at row " + (rowNum + 1));
                }

                rows.add(new ExcelRowData(
                        hasAdsTypeColumn ? adsType.trim() : "Normal",
                        adsName.trim(),
                        platformName.trim(),
                        fullUrl.trim(),
                        trimToNull(landingPageUrl),
                        parseLong(displayNumber),
                        trimToNull(remarks)));
            }
            return rows;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read Excel file", e);
        }
    }

    private Integer findHeaderIndex(Map<String, Integer> headerIndexes, Integer fallbackIndex, String... candidates) {
        Integer index = resolveHeaderIndex(headerIndexes, fallbackIndex, candidates);
        if (index != null) {
            return index;
        }
        throw new IllegalArgumentException("Missing required column: " + candidates[0]);
    }

    private Integer findOptionalHeaderIndex(Map<String, Integer> headerIndexes, Integer fallbackIndex, String... candidates) {
        return resolveHeaderIndex(headerIndexes, fallbackIndex, candidates);
    }

    private Integer resolveHeaderIndex(Map<String, Integer> headerIndexes, Integer fallbackIndex, String... candidates) {
        for (String candidate : candidates) {
            String normalizedCandidate = normalizeHeader(candidate);
            Integer exactIndex = headerIndexes.get(normalizedCandidate);
            if (exactIndex != null) {
                return exactIndex;
            }

            for (Map.Entry<String, Integer> entry : headerIndexes.entrySet()) {
                String normalizedHeader = entry.getKey();
                if (normalizedHeader.contains(normalizedCandidate) || normalizedCandidate.contains(normalizedHeader)) {
                    return entry.getValue();
                }
            }
        }
        return fallbackIndex;
    }

    private boolean looksLikeAdsTypeInFirstDataColumn(Sheet sheet, DataFormatter formatter) {
        for (int rowNum = sheet.getFirstRowNum() + 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
            Row row = sheet.getRow(rowNum);
            if (row == null) {
                continue;
            }
            String firstCellValue = trimToNull(formatter.formatCellValue(row.getCell(0)));
            if (firstCellValue == null) {
                continue;
            }
            return isSupportedAdsType(firstCellValue);
        }
        return false;
    }

    private boolean isSupportedAdsType(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return "NORMAL".equals(normalized) || "MATRIX".equals(normalized);
    }

    private boolean looksLikeLandingPageColumn(Sheet sheet, DataFormatter formatter, boolean detectedAdsTypeColumn, int headerCount) {
        int baseOffset = detectedAdsTypeColumn ? 1 : 0;
        if (headerCount < baseOffset + 6) {
            return false;
        }

        for (int rowNum = sheet.getFirstRowNum() + 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
            Row row = sheet.getRow(rowNum);
            if (row == null) {
                continue;
            }

            String landingPageCandidate = trimToNull(formatter.formatCellValue(row.getCell(baseOffset + 3)));
            String displayNumberCandidate = trimToNull(formatter.formatCellValue(row.getCell(baseOffset + 4)));
            if (landingPageCandidate == null && displayNumberCandidate == null) {
                continue;
            }

            return looksLikeUrl(landingPageCandidate) && looksLikeLong(displayNumberCandidate);
        }

        return false;
    }

    private boolean looksLikeUrl(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("http://") || normalized.startsWith("https://");
    }

    private boolean looksLikeLong(String value) {
        if (value == null) {
            return true;
        }
        try {
            Long.parseLong(value.trim());
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private Integer shiftIndexWhenFallback(Integer currentIndex, int legacyIndex, int adsTypeIndex) {
        if (currentIndex == null) {
            return null;
        }
        return currentIndex.equals(legacyIndex) ? adsTypeIndex : currentIndex;
    }

    private String readCell(Row row, Integer index, DataFormatter formatter) {
        if (index == null) {
            return null;
        }
        return formatter.formatCellValue(row.getCell(index));
    }

    private String normalizeHeader(String header) {
        String normalized = header.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        normalized = normalized.replaceAll("^_+|_+$", "");
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().replace(",", "");
        try {
            return Long.valueOf(normalized);
        } catch (NumberFormatException ex) {
            BigDecimal decimal = new BigDecimal(normalized);
            if (decimal.stripTrailingZeros().scale() > 0) {
                throw new IllegalArgumentException("Invalid whole number: " + value);
            }
            return decimal.longValueExact();
        }
    }

    private void prepareForSave(ShiftLink shiftLink, Long currentUserId) {
        if (shiftLink == null) {
            throw new IllegalArgumentException("SHIFT_LINK is required");
        }

        // Validate and resolve owner
        if (currentUserId != null) {
            User user = userRepository.findById(currentUserId)
                    .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found: " + currentUserId));
            shiftLink.setAdsOwner(user.getUserPhoneNumber());
        } else if (StringUtils.hasText(shiftLink.getAdsOwner())) {
            userRepository.findByUserPhoneNumber(shiftLink.getAdsOwner())
                    .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found by phone number: " + shiftLink.getAdsOwner()));
            shiftLink.setAdsOwner(shiftLink.getAdsOwner().trim());
        } else {
            throw new IllegalArgumentException("adsOwner is required");
        }

        // Normalize adsType
        if (StringUtils.hasText(shiftLink.getAdsType())) {
            String normalized = shiftLink.getAdsType().trim().toUpperCase();
            if ("NORMAL".equals(normalized)) {
                shiftLink.setAdsType("Normal");
            } else if ("MATRIX".equals(normalized)) {
                shiftLink.setAdsType("Matrix");
            }
        }

        if (shiftLink.getDisplayNumber() == null) {
            shiftLink.setDisplayNumber(0L);
        }
        if (shiftLink.getDisplayTimes() == null) {
            shiftLink.setDisplayTimes(0L);
        }
        if (shiftLink.getSeqNumber() == null) {
            shiftLink.setSeqNumber(1L);
        }

        validateRequiredFields(shiftLink);
        validateAdsReference(shiftLink);
        validatePlatform(shiftLink);
        normalizeStatus(shiftLink);
    }

    private void mergeForUpdate(ShiftLink existing, ShiftLink updateData, Long currentUserId) {
        if (updateData == null) {
            throw new IllegalArgumentException("updateData is required");
        }

        if (updateData.getAdsId() != null) {
            existing.setAdsId(updateData.getAdsId());
        }
        if (updateData.getAdsType() != null) {
            existing.setAdsType(updateData.getAdsType());
        }
        if (updateData.getPlatformName() != null) {
            existing.setPlatformName(updateData.getPlatformName());
        }
        if (updateData.getAdsName() != null) {
            existing.setAdsName(updateData.getAdsName());
        }
        if (updateData.getLandingPageUrl() != null) {
            existing.setLandingPageUrl(updateData.getLandingPageUrl());
        }
        if (updateData.getFullUrl() != null) {
            existing.setFullUrl(updateData.getFullUrl());
        }
        if (updateData.getUrlSuffix() != null) {
            existing.setUrlSuffix(updateData.getUrlSuffix());
        }
        if (updateData.getDisplayNumber() != null) {
            existing.setDisplayNumber(updateData.getDisplayNumber());
        }
        if (updateData.getDisplayTimes() != null) {
            existing.setDisplayTimes(updateData.getDisplayTimes());
        }
        if (updateData.getSeqNumber() != null) {
            existing.setSeqNumber(updateData.getSeqNumber());
        }
        if (updateData.getRemarks() != null) {
            existing.setRemarks(updateData.getRemarks());
        }
        if (updateData.getStatus() != null) {
            existing.setStatus(updateData.getStatus());
        }

        prepareForSave(existing, currentUserId);
        existing.setUpdateDate(LocalDateTime.now());
    }

    private void validateRequiredFields(ShiftLink shiftLink) {
        if (!StringUtils.hasText(shiftLink.getAdsType())) {
            throw new IllegalArgumentException("adsType is required");
        }
        if (!StringUtils.hasText(shiftLink.getAdsName())) {
            throw new IllegalArgumentException("adsName is required");
        }
        if (!StringUtils.hasText(shiftLink.getPlatformName())) {
            throw new IllegalArgumentException("platformName is required");
        }
        if (!StringUtils.hasText(shiftLink.getFullUrl())) {
            throw new IllegalArgumentException("fullUrl is required");
        }
        if (!StringUtils.hasText(shiftLink.getAdsOwner())) {
            throw new IllegalArgumentException("adsOwner is required");
        }

        String normalizedAdsType = shiftLink.getAdsType().trim().toUpperCase();
        if (!"NORMAL".equals(normalizedAdsType) && !"MATRIX".equals(normalizedAdsType)) {
            throw new IllegalArgumentException("adsType must be Normal or Matrix");
        }

        if (shiftLink.getAdsName().length() > 32) {
            throw new IllegalArgumentException("adsName must be at most 32 characters");
        }
        if (shiftLink.getFullUrl().length() > 512) {
            throw new IllegalArgumentException("fullUrl must be at most 512 characters");
        }
        if (shiftLink.getPlatformName().length() > 32) {
            throw new IllegalArgumentException("platformName must be at most 32 characters");
        }
        if (shiftLink.getRemarks() != null && shiftLink.getRemarks().length() > 64) {
            throw new IllegalArgumentException("remarks must be at most 64 characters");
        }
        if (shiftLink.getAdsOwner().length() > 32) {
            throw new IllegalArgumentException("adsOwner must be at most 32 characters");
        }
    }

    private void validateAdsReference(ShiftLink shiftLink) {
        String adsType = shiftLink.getAdsType();
        String adsName = shiftLink.getAdsName();
        String adsOwner = shiftLink.getAdsOwner();

        if (!StringUtils.hasText(adsType) || !StringUtils.hasText(adsName)) {
            return;
        }

        String normalizedAdsType = adsType.trim().toUpperCase();

        validateScopedSeqNumberUniqueness(shiftLink);

        shiftLink.setAdsId(resolveAdsId(adsName, adsOwner, normalizedAdsType));
    }

    private void validateScopedSeqNumberUniqueness(ShiftLink shiftLink) {
        String adsName = shiftLink.getAdsName();
        String adsOwner = shiftLink.getAdsOwner();
        String adsType = shiftLink.getAdsType().trim().toUpperCase();
        Long seqNumber = shiftLink.getSeqNumber();
        Long currentId = shiftLink.getId();

        var existingLinks = shiftLinkRepository.findAll((root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("adsName"), adsName));
            predicates.add(cb.equal(root.get("adsOwner"), adsOwner));
            predicates.add(cb.equal(root.get("adsType"), adsType));
            predicates.add(cb.equal(root.get("seqNumber"), seqNumber));

            if (currentId != null) {
                predicates.add(cb.notEqual(root.get("id"), currentId));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        });

        if (!existingLinks.isEmpty()) {
            throw new IllegalArgumentException(
                    "seqNumber '" + seqNumber + "' is already used for adsName '" + adsName
                            + "', adsType '" + adsType + "', owner '" + adsOwner + "'");
        }
    }

    private Long resolveAdsId(String adsName, String adsOwner, String normalizedAdsType) {
        if ("NORMAL".equals(normalizedAdsType)) {
            return adsNormalInfoRepository.findByCampainNameAndAdsOwner(adsName, adsOwner)
                    .map(AdsNormalInfo::getId)
                    .orElse(0L);
        }
        if ("MATRIX".equals(normalizedAdsType)) {
            return adsMatrixInfoRepository.findByCampainNameAndAdsOwner(adsName, adsOwner)
                    .map(AdsMatrixInfo::getId)
                    .orElse(0L);
        }
        throw new IllegalArgumentException("adsType must be Normal or Matrix");
    }

    private void validatePlatform(ShiftLink shiftLink) {
        AdsPlatform platform = adsPlatformRepository.findByPlatformNameIgnoreCase(shiftLink.getPlatformName())
                .orElseThrow(() -> new IllegalArgumentException("ADS_PLATFORM not found: " + shiftLink.getPlatformName()));
        shiftLink.setPlatformName(platform.getPlatformName());
    }

    private String normalizeAdsTypeValue(String adsType) {
        if (!StringUtils.hasText(adsType)) {
            throw new IllegalArgumentException("adsType is required");
        }
        String normalized = adsType.trim().toUpperCase(Locale.ROOT);
        if ("NORMAL".equals(normalized)) {
            return "Normal";
        }
        if ("MATRIX".equals(normalized)) {
            return "Matrix";
        }
        throw new IllegalArgumentException("adsType must be Normal or Matrix");
    }

    private void deleteShiftLinksByScope(String adsOwner, String adsName, String adsType) {
        List<ShiftLink> existingLinks = shiftLinkRepository
                .findByAdsOwnerAndAdsNameAndAdsTypeOrderBySeqNumberAsc(adsOwner, adsName, adsType);
        for (ShiftLink existingLink : existingLinks) {
            createAuditEntry(existingLink, "DELETE");
        }
        shiftLinkRepository.deleteAll(existingLinks);
    }

    private String buildScopeKey(String adsOwner, String adsName, String adsType) {
        return adsOwner + "|" + adsName + "|" + adsType;
    }

    private void normalizeStatus(ShiftLink shiftLink) {
        if (shiftLink.getStatus() == null || shiftLink.getStatus().isBlank()) {
            shiftLink.setStatus("RUNNING");
        }
        String normalizedStatus = shiftLink.getStatus().trim().toUpperCase();
        if (!"PAUSED".equals(normalizedStatus) && !"RUNNING".equals(normalizedStatus)) {
            throw new IllegalArgumentException("status must be PAUSED or RUNNING");
        }
        shiftLink.setStatus(normalizedStatus);
    }

    private void createAuditEntry(ShiftLink shiftLink, String operation) {
        ShiftLinkAud audit = new ShiftLinkAud();
        audit.setShiftLinkId(shiftLink.getId());
        audit.setAdsOwner(shiftLink.getAdsOwner());
        audit.setAdsName(shiftLink.getAdsName());
        audit.setAdsType(shiftLink.getAdsType());
        audit.setSeqNumber(shiftLink.getSeqNumber());
        audit.setOperation(operation);
        audit.setOperationDate(LocalDateTime.now());
        shiftLinkAudRepository.save(audit);
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

    public record BulkUploadResult(int rowCount, int insertedCount) {
    }

    private record ExcelRowData(String adsType, String adsName, String platformName, String fullUrl, String landingPageUrl, Long displayNumber, String remarks) {
    }
}
