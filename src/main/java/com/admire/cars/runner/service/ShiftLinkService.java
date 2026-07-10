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
import org.apache.commons.compress.utils.Lists;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

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

        Map<String,List<ExcelRowData>> rowsByAdsType = rows.stream().collect(Collectors.groupingBy(ExcelRowData :: adsType, Collectors.toList()));
        AtomicInteger insertedCount = new AtomicInteger(0);
        rowsByAdsType.keySet().forEach(adsType -> {
            if (!"Normal".equals(adsType) && !"Matrix".equals(adsType)) {
                throw new IllegalArgumentException("Invalid adsType in Excel: " + adsType);
            }
            List<ExcelRowData> rowsByAdsTypeList = rowsByAdsType.get(adsType);
            rowsByAdsTypeList.stream().collect(Collectors.groupingBy(ExcelRowData::adsName, Collectors.toList()))
                    .forEach((adsName, groupedRows) -> {
                        //delete existing shift links for this adsName and adsType
                        deleteShiftLinksByScope(owner.getUserPhoneNumber(), adsName, adsType);
                        String normalizedAdsType = adsType.trim().toUpperCase();
                        Long adsId = resolveAdsId(adsName, owner.getUserPhoneNumber(), normalizedAdsType);
                        AtomicReference<Long> sequenceNum = new AtomicReference<>(0L);
                        List<ShiftLink> shiftLinkList = Lists.newArrayList();
                        //Initial shift link for bulk saving
                        groupedRows.stream().forEach(row -> {
                            ShiftLink shiftLink = new ShiftLink();
                            shiftLink.setAdsType(row.adsType());
                            shiftLink.setAdsId(adsId);
                            shiftLink.setAdsName(row.adsName());
                            shiftLink.setPlatformName(row.platformName());
                            shiftLink.setFullUrl(row.fullUrl());
                            shiftLink.setLandingPageUrl(row.landingPageUrl());
                            shiftLink.setDisplayNumber(row.displayNumber() != null ? row.displayNumber() : 5L);
                            shiftLink.setRemarks(row.remarks());
                            shiftLink.setAdsOwner(owner.getUserPhoneNumber());
                            shiftLink.setSeqNumber(sequenceNum.get() + 1);
                            sequenceNum.getAndSet(sequenceNum.get() + 1);
                            shiftLinkList.add(shiftLink);
                        });
                        shiftLinkRepository.saveAll(shiftLinkList);
                        insertedCount.addAndGet(shiftLinkList.size());
            });
        });
        return new BulkUploadResult(rows.size(), insertedCount.get());
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
            int headerCount = Math.max(headerRow.getLastCellNum(), 0);
            if (headerCount < 7) {
                throw new IllegalArgumentException("Excel header row has insufficient columns");

            }
            validateExcelHeader(headerRow, formatter);

            List<ExcelRowData> rows = new ArrayList<>();
            for (int rowNum = sheet.getFirstRowNum() + 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null) {
                    continue;
                }
                String adsType = readCell(row, 0, formatter);
                String adsName = readCell(row, 1, formatter);
                String platformName = readCell(row, 2, formatter);
                String fullUrl = readCell(row, 3, formatter);
                String landingPageUrl = readCell(row, 4, formatter);
                String displayNumber = readCell(row, 5, formatter);
                String remarks = readCell(row, 6, formatter);
                if (isSupportedAdsType(adsType)) {
                    rows.add(new ExcelRowData(
                            adsType,
                            adsName.trim(),
                            platformName.trim(),
                            fullUrl.trim(),
                            trimToNull(landingPageUrl),
                            parseLong(displayNumber),
                            trimToNull(remarks)));
                }
            }
            return rows;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read Excel file", e);
        }
    }



    private void validateExcelHeader (Row headerRow, DataFormatter formatter) {
        if (headerRow.getCell(0) == null || !StringUtils.hasText(formatter.formatCellValue(headerRow.getCell(0))) ||
                !"adsType".equals(formatter.formatCellValue(headerRow.getCell(0)).trim())) {
            throw new IllegalArgumentException("Excel header row first column should be adsType");
        }
        if (headerRow.getCell(1) == null || !StringUtils.hasText(formatter.formatCellValue(headerRow.getCell(1))) ||
                !"adsName".equals(formatter.formatCellValue(headerRow.getCell(1)).trim())) {
            throw new IllegalArgumentException("Excel header row second column should be adsName");
        }
        if (headerRow.getCell(2) == null || !StringUtils.hasText(formatter.formatCellValue(headerRow.getCell(2))) ||
                !"platformName".equals(formatter.formatCellValue(headerRow.getCell(2)).trim())) {
            throw new IllegalArgumentException("Excel header row third column should be platformName");
        }
        if (headerRow.getCell(3) == null || !StringUtils.hasText(formatter.formatCellValue(headerRow.getCell(3))) ||
                !"fullUrl".equals(formatter.formatCellValue(headerRow.getCell(3)).trim())) {
            throw new IllegalArgumentException("Excel header row fourth column should be fullUrl");
        }
        if (headerRow.getCell(4) == null || !StringUtils.hasText(formatter.formatCellValue(headerRow.getCell(4))) ||
                !"landingPageUrl".equals(formatter.formatCellValue(headerRow.getCell(4)).trim())) {
            throw new IllegalArgumentException("Excel header row fifth column should be landingPageUrl");
        }
        if (headerRow.getCell(5) == null || !StringUtils.hasText(formatter.formatCellValue(headerRow.getCell(5))) ||
                !"displayNumber".equals(formatter.formatCellValue(headerRow.getCell(5)).trim())) {
            throw new IllegalArgumentException("Excel header row sixth column should be displayNumber");
        }
        if (headerRow.getCell(6) == null || !StringUtils.hasText(formatter.formatCellValue(headerRow.getCell(6))) ||
                !"remarks".equals(formatter.formatCellValue(headerRow.getCell(6)).trim())) {
            throw new IllegalArgumentException("Excel header row seventh column should be remarks");
        }
    }


    private boolean isSupportedAdsType(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return "NORMAL".equals(normalized) || "MATRIX".equals(normalized);
    }


    private String readCell(Row row, Integer index, DataFormatter formatter) {
        if (index == null) {
            return null;
        }
        return formatter.formatCellValue(row.getCell(index));
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
            try {
                BigDecimal decimal = new BigDecimal(normalized);
                if (decimal.stripTrailingZeros().scale() > 0) {
                    throw new IllegalArgumentException("Invalid whole number: " + value);
                }
                return decimal.longValueExact();
            } catch (NumberFormatException decimalEx) {
                throw new IllegalArgumentException("Invalid whole number: " + value);
            }
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
            shiftLink.setDisplayNumber(5L);
        }
        shiftLink.setDisplayTimes(0L);

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

        shiftLink.setSeqNumber(calculateSequenceNum(shiftLink));

        shiftLink.setAdsId(resolveAdsId(adsName, adsOwner, normalizedAdsType));
    }

    private Long calculateSequenceNum(ShiftLink shiftLink) {
        String adsName = shiftLink.getAdsName();
        String adsOwner = shiftLink.getAdsOwner();
        String adsType = shiftLink.getAdsType();
        Long maxSeqNumber = shiftLinkRepository
                .findTopByAdsOwnerAndAdsNameAndAdsTypeOrderBySeqNumberDesc(adsOwner, adsName, adsType)
                .map(ShiftLink::getSeqNumber)
                .orElse(0L);
        return maxSeqNumber + 1L;
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



    private void deleteShiftLinksByScope(String adsOwner, String adsName, String adsType) {
        List<ShiftLink> existingLinks = shiftLinkRepository
                .findByAdsOwnerAndAdsNameAndAdsTypeOrderBySeqNumberAsc(adsOwner, adsName, adsType);
        for (ShiftLink existingLink : existingLinks) {
            createAuditEntry(existingLink, "DELETE");
        }
        shiftLinkRepository.deleteAll(existingLinks);
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
