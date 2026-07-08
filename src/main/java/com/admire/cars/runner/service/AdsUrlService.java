package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AdsMatrixInfo;
import com.admire.cars.runner.entity.AdsNormalInfo;
import com.admire.cars.runner.entity.AdsUrl;
import com.admire.cars.runner.entity.AdsUrlAud;
import com.admire.cars.runner.entity.AdsPlatform;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.AdsMatrixInfoRepository;
import com.admire.cars.runner.repository.AdsNormalInfoRepository;
import com.admire.cars.runner.repository.AdsPlatformRepository;
import com.admire.cars.runner.repository.AdsUrlAudRepository;
import com.admire.cars.runner.repository.AdsUrlRepository;
import com.admire.cars.runner.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class AdsUrlService {

    private final AdsUrlRepository adsUrlRepository;
    private final AdsUrlAudRepository adsUrlAudRepository;
    private final UserRepository userRepository;
    private final AdsPlatformRepository adsPlatformRepository;
    private final AdsNormalInfoRepository adsNormalInfoRepository;
    private final AdsMatrixInfoRepository adsMatrixInfoRepository;

    public AdsUrlService(
            AdsUrlRepository adsUrlRepository,
            AdsUrlAudRepository adsUrlAudRepository,
            UserRepository userRepository,
            AdsPlatformRepository adsPlatformRepository,
            AdsNormalInfoRepository adsNormalInfoRepository,
            AdsMatrixInfoRepository adsMatrixInfoRepository) {
        this.adsUrlRepository = adsUrlRepository;
        this.adsUrlAudRepository = adsUrlAudRepository;
        this.userRepository = userRepository;
        this.adsPlatformRepository = adsPlatformRepository;
        this.adsNormalInfoRepository = adsNormalInfoRepository;
        this.adsMatrixInfoRepository = adsMatrixInfoRepository;
    }

    public AdsUrl createAdsUrl(AdsUrl adsUrl) {
        return createAdsUrl(adsUrl, null);
    }

    public AdsUrl createAdsUrl(AdsUrl adsUrl, Long currentUserId) {
        prepareForSave(adsUrl, currentUserId);
        AdsUrl saved = adsUrlRepository.save(adsUrl);
        createAuditEntry(saved, 1L);
        return saved;
    }

    @Transactional(readOnly = true)
    public AdsUrl getAdsUrlById(Long id) {
        return adsUrlRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ADS_URL not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<AdsUrl> getAllAdsUrls() {
        return adsUrlRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<AdsUrl> getAdsUrlsByCampaignOwner(Long campaignOwner) {
        return adsUrlRepository.findByCampaignOwner(campaignOwner);
    }

    @Transactional(readOnly = true)
    public List<AdsUrl> getAdsUrlsByCapMainName(String capMainName, Long campaignOwner) {
        return adsUrlRepository.findByCapMainNameAndCampaignOwner(capMainName, campaignOwner);
    }

    @Transactional(readOnly = true)
    public List<AdsUrl> getAdsUrlsByPlatform(String platform) {
        return adsUrlRepository.findByPlatform(platform);
    }

    @Transactional(readOnly = true)
    public Page<AdsUrl> searchAdsUrls(String platformName, String status, String adsOwner, Pageable pageable) {
        Specification<AdsUrl> specification = (root, query, criteriaBuilder) -> {
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
            if (StringUtils.hasText(adsOwner)) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("adsOwner")),
                        "%" + adsOwner.toLowerCase() + "%"));
            }
            return predicates.isEmpty()
                    ? criteriaBuilder.conjunction()
                    : criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        return adsUrlRepository.findAll(specification, pageable);
    }

    public AdsUrl updateAdsUrl(Long id, AdsUrl updateData) {
        return updateAdsUrl(id, updateData, null);
    }

    public AdsUrl updateAdsUrl(Long id, AdsUrl updateData, Long currentUserId) {
        AdsUrl existing = getAdsUrlById(id);
        if (currentUserId != null && existing.getCampaignOwner() != null && !existing.getCampaignOwner().equals(currentUserId)) {
            throw new IllegalArgumentException("Unauthorized: you can only update your own URLs");
        }

        mergeForUpdate(existing, updateData, currentUserId);
        Long revision = nextRevision(existing);
        existing.setVersion(revision);
        AdsUrl updated = adsUrlRepository.save(existing);
        createAuditEntry(updated, revision);
        return updated;
    }

    public void deleteAdsUrl(Long id) {
        deleteAdsUrl(id, null);
    }

    public void deleteAdsUrl(Long id, Long currentUserId) {
        AdsUrl existing = getAdsUrlById(id);
        if (currentUserId != null && existing.getCampaignOwner() != null && !existing.getCampaignOwner().equals(currentUserId)) {
            throw new IllegalArgumentException("Unauthorized: you can only delete your own URLs");
        }
        Long revision = nextRevision(existing);
        existing.setVersion(revision);
        adsUrlRepository.delete(existing);
        createAuditEntry(existing, revision);
    }

    @Transactional(readOnly = true)
    public List<AdsUrlAud> getAdsUrlAuditHistory(Long id) {
        return adsUrlAudRepository.findByIdOrderByReversionDesc(id);
    }

    public void incrementDisplayTimes(Long id) {
        AdsUrl adsUrl = getAdsUrlById(id);
        adsUrl.setDisplayTimes((adsUrl.getDisplayTimes() != null ? adsUrl.getDisplayTimes() : 0L) + 1L);
        adsUrl.setDisplayNumber(adsUrl.getDisplayTimes());
        Long revision = nextRevision(adsUrl);
        adsUrl.setVersion(revision);
        adsUrlRepository.save(adsUrl);
        createAuditEntry(adsUrl, revision);
    }

    public BulkUploadResult replaceByExcel(MultipartFile file, Long currentUserId, String adsType, Long campainId) {
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
        String normalizedAdsType = normalizeAdsType(adsType);

        Map<String, List<ExcelRowData>> groupedByCampaign = new LinkedHashMap<>();
        for (ExcelRowData row : rows) {
            String key = row.campaignName() + "|" + row.campaignCountry();
            groupedByCampaign.computeIfAbsent(key, value -> new ArrayList<>()).add(row);
        }

        int deletedCount = 0;
        int insertedCount = 0;

        for (Map.Entry<String, List<ExcelRowData>> entry : groupedByCampaign.entrySet()) {
            ExcelRowData first = entry.getValue().get(0);
            List<AdsUrl> existingRows = adsUrlRepository.findAll((root, query, cb) -> cb.and(
                    cb.equal(cb.lower(root.get("campainName")), first.campaignName().toLowerCase()),
                    cb.equal(cb.lower(root.get("campainCountry")), first.campaignCountry().toLowerCase()),
                    cb.equal(cb.lower(root.get("status")), "running"),
                    cb.equal(cb.lower(root.get("adsOwner")), owner.getUserPhoneNumber().toLowerCase())
            ));
            for (AdsUrl existing : existingRows) {
                adsUrlRepository.delete(existing);
                createAuditEntry(existing, nextRevision(existing));
                deletedCount++;
            }

            long seq = 1L;
            for (ExcelRowData row : entry.getValue()) {
                AdsUrl adsUrl = new AdsUrl();
                adsUrl.setCampainName(row.campaignName());
                adsUrl.setCapMainName(row.campaignName());
                adsUrl.setCampainCountry(row.campaignCountry());
                adsUrl.setPlatformName(row.platformName());
                adsUrl.setPlatform(row.platformName());
                adsUrl.setFullUrl(row.fullUrl());
                adsUrl.setDisplayNumber(row.displayNumber());
                adsUrl.setDisplayTimes(row.displayNumber());
                adsUrl.setRemarks(row.remarks());
                adsUrl.setRemark(row.remarks());
                adsUrl.setAdsType(normalizedAdsType);
                adsUrl.setCampainId(campainId);
                adsUrl.setCampaignOwner(currentUserId);
                adsUrl.setAdsOwner(owner.getUserPhoneNumber());
                adsUrl.setSeqNumber(seq++);
                adsUrl.setStatus("RUNNING");
                createAdsUrl(adsUrl, currentUserId);
                insertedCount++;
            }
        }

        return new BulkUploadResult(groupedByCampaign.size(), rows.size(), deletedCount, insertedCount);
    }

    public byte[] createTemplateWorkbook() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("ADS_URL_TEMPLATE");
            Row header = sheet.createRow(0);
            String[] headers = {
                    "Campain Name",
                    "Campain Country",
                    "Platform",
                    "Full URL",
                    "Display Number",
                    "Remarks"
            };
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setFillForegroundColor((short) 22);
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
            return out.toByteArray();
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

            Integer campaignIndex = findHeaderIndex(headerIndexes, "campain_name", "campaign_name");
            Integer countryIndex = findHeaderIndex(headerIndexes, "campain_country", "campaign_country");
            Integer platformIndex = findHeaderIndex(headerIndexes, "platform");
            Integer fullUrlIndex = findHeaderIndex(headerIndexes, "full_url");
            Integer displayNumberIndex = findHeaderIndex(headerIndexes, "display_number");
            Integer remarksIndex = findHeaderIndex(headerIndexes, "remarks", "remark");

            List<ExcelRowData> rows = new ArrayList<>();
            for (int rowNum = sheet.getFirstRowNum() + 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null) {
                    continue;
                }

                String campaignName = readCell(row, campaignIndex, formatter);
                String campaignCountry = readCell(row, countryIndex, formatter);
                String platformName = readCell(row, platformIndex, formatter);
                String fullUrl = readCell(row, fullUrlIndex, formatter);
                String displayNumber = readCell(row, displayNumberIndex, formatter);
                String remarks = readCell(row, remarksIndex, formatter);

                if ((campaignName == null || campaignName.isBlank())
                        && (campaignCountry == null || campaignCountry.isBlank())
                        && (platformName == null || platformName.isBlank())
                        && (fullUrl == null || fullUrl.isBlank())
                        && (displayNumber == null || displayNumber.isBlank())
                        && (remarks == null || remarks.isBlank())) {
                    continue;
                }

                if (!StringUtils.hasText(campaignName)) {
                    throw new IllegalArgumentException("Campain Name is required at row " + (rowNum + 1));
                }
                if (!StringUtils.hasText(campaignCountry)) {
                    throw new IllegalArgumentException("Campain Country is required at row " + (rowNum + 1));
                }
                if (!StringUtils.hasText(platformName)) {
                    throw new IllegalArgumentException("Platform is required at row " + (rowNum + 1));
                }
                if (!StringUtils.hasText(fullUrl)) {
                    throw new IllegalArgumentException("Full URL is required at row " + (rowNum + 1));
                }

                rows.add(new ExcelRowData(
                        campaignName.trim(),
                        campaignCountry.trim(),
                        platformName.trim(),
                        fullUrl.trim(),
                        parseLong(displayNumber),
                        trimToNull(remarks)));
            }
            return rows;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read Excel file", e);
        }
    }

    private Integer findHeaderIndex(Map<String, Integer> headerIndexes, String... candidates) {
        for (String candidate : candidates) {
            Integer index = headerIndexes.get(normalizeHeader(candidate));
            if (index != null) {
                return index;
            }
        }
        throw new IllegalArgumentException("Missing required column: " + candidates[0]);
    }

    private String readCell(Row row, Integer index, DataFormatter formatter) {
        if (index == null) {
            return null;
        }
        return formatter.formatCellValue(row.getCell(index));
    }

    private String normalizeHeader(String header) {
        return header.trim().toLowerCase(Locale.ROOT);
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
        return Long.valueOf(value.trim());
    }

    private void prepareForSave(AdsUrl adsUrl, Long currentUserId) {
        if (adsUrl == null) {
            throw new IllegalArgumentException("ADS_URL is required");
        }

        normalizeAliases(adsUrl);
        if (currentUserId != null) {
            User user = userRepository.findById(currentUserId)
                    .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found: " + currentUserId));
            adsUrl.setCampaignOwner(currentUserId);
            adsUrl.setAdsOwner(user.getUserPhoneNumber());
        } else if (adsUrl.getCampaignOwner() == null && StringUtils.hasText(adsUrl.getAdsOwner())) {
            User user = userRepository.findByUserPhoneNumber(adsUrl.getAdsOwner())
                    .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found by phone number: " + adsUrl.getAdsOwner()));
            adsUrl.setCampaignOwner(user.getId());
        } else if (!StringUtils.hasText(adsUrl.getAdsOwner()) && adsUrl.getCampaignOwner() != null) {
            User user = userRepository.findById(adsUrl.getCampaignOwner())
                    .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found: " + adsUrl.getCampaignOwner()));
            adsUrl.setAdsOwner(user.getUserPhoneNumber());
        } else if (StringUtils.hasText(adsUrl.getAdsOwner())) {
            userRepository.findByUserPhoneNumber(adsUrl.getAdsOwner())
                    .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found by phone number: " + adsUrl.getAdsOwner()));
        }

        validateRequiredFields(adsUrl);
        normalizeAndValidatePlatform(adsUrl);
        normalizeAndValidateCampaignReference(adsUrl);

        if (adsUrl.getStatus() == null || adsUrl.getStatus().isBlank()) {
            adsUrl.setStatus("RUNNING");
        }
        String normalizedStatus = adsUrl.getStatus().trim().toUpperCase();
        if (!"PAUSED".equals(normalizedStatus) && !"RUNNING".equals(normalizedStatus)) {
            throw new IllegalArgumentException("status must be PAUSED or RUNNING");
        }
        adsUrl.setStatus(normalizedStatus);

        if (adsUrl.getAdsType() == null || adsUrl.getAdsType().isBlank()) {
            adsUrl.setAdsType("Normal");
        }
        adsUrl.setAdsType(normalizeAdsType(adsUrl.getAdsType()));

        if (adsUrl.getDisplayNumber() == null && adsUrl.getDisplayTimes() != null) {
            adsUrl.setDisplayNumber(adsUrl.getDisplayTimes());
        }
        if (adsUrl.getDisplayTimes() == null && adsUrl.getDisplayNumber() != null) {
            adsUrl.setDisplayTimes(adsUrl.getDisplayNumber());
        }
        if (adsUrl.getDisplayTimes() == null) {
            adsUrl.setDisplayTimes(0L);
        }
        if (adsUrl.getDisplayNumber() == null) {
            adsUrl.setDisplayNumber(adsUrl.getDisplayTimes());
        }

        if (adsUrl.getSeqNumber() == null) {
            adsUrl.setSeqNumber(1L);
        }
        if (adsUrl.getUrlSuffix() != null && adsUrl.getUrlSuffix().length() > 256) {
            throw new IllegalArgumentException("urlSuffix must be at most 256 characters");
        }

        adsUrl.setCreateDate(adsUrl.getCreateDate() == null ? LocalDateTime.now() : adsUrl.getCreateDate());
        adsUrl.setLandingUrl(parseLandingUrl(adsUrl.getFullUrl()));
        adsUrl.setLandingPageUrl(adsUrl.getLandingPageUrl() == null ? adsUrl.getLandingUrl() : adsUrl.getLandingPageUrl());
        adsUrl.setUrlSuffix(parseUrlSuffix(adsUrl.getFullUrl()));
        adsUrl.setPlatformName(adsUrl.getPlatformName() == null ? adsUrl.getPlatform() : adsUrl.getPlatformName());
        adsUrl.setPlatform(adsUrl.getPlatformName());
        adsUrl.setCapMainName(adsUrl.getCampainName());
        adsUrl.setRemark(adsUrl.getRemarks());
        adsUrl.setCampaignOwner(adsUrl.getCampaignOwner() == null && currentUserId != null ? currentUserId : adsUrl.getCampaignOwner());
        adsUrl.setAdsOwner(adsUrl.getAdsOwner().trim());
        if (adsUrl.getCampainCountry() != null) {
            adsUrl.setCampainCountry(adsUrl.getCampainCountry().trim());
            if (adsUrl.getCampainCountry().length() > 8) {
                throw new IllegalArgumentException("campainCountry must be at most 8 characters");
            }
        }
    }

    private void mergeForUpdate(AdsUrl existing, AdsUrl updateData, Long currentUserId) {
        if (updateData == null) {
            throw new IllegalArgumentException("updateData is required");
        }
        normalizeAliases(updateData);
        if (updateData.getCapMainName() != null) {
            existing.setCapMainName(updateData.getCapMainName());
        }
        if (updateData.getCampainName() != null) {
            existing.setCampainName(updateData.getCampainName());
        }
        if (updateData.getCampainCountry() != null) {
            existing.setCampainCountry(updateData.getCampainCountry());
        }
        if (updateData.getFullUrl() != null) {
            existing.setFullUrl(updateData.getFullUrl());
        }
        if (updateData.getLandingPageUrl() != null) {
            existing.setLandingPageUrl(updateData.getLandingPageUrl());
        }
        if (updateData.getLandingUrl() != null) {
            existing.setLandingUrl(updateData.getLandingUrl());
        }
        if (updateData.getUrlSuffix() != null) {
            existing.setUrlSuffix(updateData.getUrlSuffix());
        }
        if (updateData.getPlatformName() != null) {
            existing.setPlatformName(updateData.getPlatformName());
        }
        if (updateData.getPlatform() != null) {
            existing.setPlatform(updateData.getPlatform());
        }
        if (updateData.getRemarks() != null) {
            existing.setRemarks(updateData.getRemarks());
        }
        if (updateData.getRemark() != null) {
            existing.setRemark(updateData.getRemark());
        }
        if (updateData.getDisplayNumber() != null) {
            existing.setDisplayNumber(updateData.getDisplayNumber());
            existing.setDisplayTimes(updateData.getDisplayNumber());
        }
        if (updateData.getDisplayTimes() != null) {
            existing.setDisplayTimes(updateData.getDisplayTimes());
            existing.setDisplayNumber(updateData.getDisplayTimes());
        }
        if (updateData.getSeqNumber() != null) {
            existing.setSeqNumber(updateData.getSeqNumber());
        }
        if (updateData.getAdsType() != null) {
            existing.setAdsType(updateData.getAdsType());
        }
        if (updateData.getCampainId() != null) {
            existing.setCampainId(updateData.getCampainId());
        }
        if (updateData.getStatus() != null) {
            existing.setStatus(updateData.getStatus());
        }
        if (updateData.getAdsOwner() != null) {
            existing.setAdsOwner(updateData.getAdsOwner());
        }
        if (currentUserId != null) {
            User user = userRepository.findById(currentUserId)
                    .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found: " + currentUserId));
            existing.setCampaignOwner(currentUserId);
            existing.setAdsOwner(user.getUserPhoneNumber());
        } else if (StringUtils.hasText(existing.getAdsOwner())) {
            userRepository.findByUserPhoneNumber(existing.getAdsOwner())
                    .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found by phone number: " + existing.getAdsOwner()));
        }
        prepareForSave(existing, null);
        existing.setUpdateDate(LocalDateTime.now());
    }

    private void normalizeAliases(AdsUrl adsUrl) {
        if (adsUrl.getCampainName() == null && adsUrl.getCapMainName() != null) {
            adsUrl.setCampainName(adsUrl.getCapMainName());
        }
        if (adsUrl.getCapMainName() == null && adsUrl.getCampainName() != null) {
            adsUrl.setCapMainName(adsUrl.getCampainName());
        }
        if (adsUrl.getLandingPageUrl() == null && adsUrl.getLandingUrl() != null) {
            adsUrl.setLandingPageUrl(adsUrl.getLandingUrl());
        }
        if (adsUrl.getLandingUrl() == null && adsUrl.getLandingPageUrl() != null) {
            adsUrl.setLandingUrl(adsUrl.getLandingPageUrl());
        }
        if (adsUrl.getPlatformName() == null && adsUrl.getPlatform() != null) {
            adsUrl.setPlatformName(adsUrl.getPlatform());
        }
        if (adsUrl.getPlatform() == null && adsUrl.getPlatformName() != null) {
            adsUrl.setPlatform(adsUrl.getPlatformName());
        }
        if (adsUrl.getRemarks() == null && adsUrl.getRemark() != null) {
            adsUrl.setRemarks(adsUrl.getRemark());
        }
        if (adsUrl.getRemark() == null && adsUrl.getRemarks() != null) {
            adsUrl.setRemark(adsUrl.getRemarks());
        }
    }

    private void validateRequiredFields(AdsUrl adsUrl) {
        if (!StringUtils.hasText(adsUrl.getCapMainName())) {
            throw new IllegalArgumentException("campainName is required");
        }
        if (!StringUtils.hasText(adsUrl.getFullUrl())) {
            throw new IllegalArgumentException("fullUrl is required");
        }
        if (!StringUtils.hasText(adsUrl.getPlatformName())) {
            throw new IllegalArgumentException("platformName is required");
        }
        if (!StringUtils.hasText(adsUrl.getAdsOwner())) {
            throw new IllegalArgumentException("adsOwner is required");
        }
        if (adsUrl.getCampainName().length() > 32) {
            throw new IllegalArgumentException("campainName must be at most 32 characters");
        }
        if (adsUrl.getCampainCountry() != null && adsUrl.getCampainCountry().length() > 8) {
            throw new IllegalArgumentException("campainCountry must be at most 8 characters");
        }
        if (adsUrl.getFullUrl().length() > 512) {
            throw new IllegalArgumentException("fullUrl must be at most 512 characters");
        }
        if (adsUrl.getPlatformName().length() > 32) {
            throw new IllegalArgumentException("platformName must be at most 32 characters");
        }
        if (adsUrl.getRemarks() != null && adsUrl.getRemarks().length() > 64) {
            throw new IllegalArgumentException("remarks must be at most 64 characters");
        }
        if (adsUrl.getAdsOwner().length() > 32) {
            throw new IllegalArgumentException("adsOwner must be at most 32 characters");
        }
    }

    private void normalizeAndValidatePlatform(AdsUrl adsUrl) {
        AdsPlatform platform = adsPlatformRepository.findByPlatformNameIgnoreCase(adsUrl.getPlatformName())
                .orElseThrow(() -> new IllegalArgumentException("ADS_PLATFORM not found: " + adsUrl.getPlatformName()));
        adsUrl.setPlatformName(platform.getPlatformName());
        adsUrl.setPlatform(platform.getPlatformName());
    }

    private void normalizeAndValidateCampaignReference(AdsUrl adsUrl) {
        if (adsUrl.getCampainId() == null) {
            return;
        }
        String adsType = normalizeAdsType(adsUrl.getAdsType());
        adsUrl.setAdsType(adsType);
        if ("NORMAL".equalsIgnoreCase(adsType)) {
            AdsNormalInfo normalInfo = adsNormalInfoRepository.findById(adsUrl.getCampainId())
                    .orElseThrow(() -> new IllegalArgumentException("ADS_NORMAL_INFO not found: " + adsUrl.getCampainId()));
            adsUrl.setCampainName(normalInfo.getCampainName());
            adsUrl.setCampainCountry(normalInfo.getCampainCountry());
        } else if ("MATRIX".equalsIgnoreCase(adsType)) {
            AdsMatrixInfo matrixInfo = adsMatrixInfoRepository.findById(adsUrl.getCampainId())
                    .orElseThrow(() -> new IllegalArgumentException("ADS_MATRIX_INFO not found: " + adsUrl.getCampainId()));
            adsUrl.setCampainName(matrixInfo.getCampainName());
            adsUrl.setCampainCountry(matrixInfo.getCampainCountry());
        } else {
            throw new IllegalArgumentException("adsType must be Normal or Matrix");
        }
    }

    private String normalizeAdsType(String adsType) {
        if (!StringUtils.hasText(adsType)) {
            return "Normal";
        }
        String normalized = adsType.trim().toLowerCase(Locale.ROOT);
        if ("normal".equals(normalized)) {
            return "Normal";
        }
        if ("matrix".equals(normalized)) {
            return "Matrix";
        }
        throw new IllegalArgumentException("adsType must be Normal or Matrix");
    }

    private String parseLandingUrl(String fullUrl) {
        String normalized = fullUrl == null ? null : fullUrl.trim();
        if (normalized == null || normalized.isEmpty()) {
            throw new IllegalArgumentException("fullUrl is required");
        }
        try {
            URI uri = URI.create(normalized);
            String scheme = uri.getScheme();
            String authority = uri.getRawAuthority();
            if (scheme == null || authority == null) {
                throw new IllegalArgumentException("Invalid fullUrl: " + fullUrl);
            }
            String path = uri.getRawPath();
            if (path == null || path.isBlank()) {
                path = "/";
            }
            return scheme + "://" + authority + path;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid fullUrl: " + fullUrl, e);
        }
    }

    private String parseUrlSuffix(String fullUrl) {
        return fullUrl == null ? null : fullUrl.trim();
    }

    private void createAuditEntry(AdsUrl adsUrl, Long reversion) {
        AdsUrlAud audit = new AdsUrlAud();
        audit.setId(adsUrl.getId());
        audit.setCapMainName(adsUrl.getCapMainName());
        audit.setCampainName(adsUrl.getCampainName());
        audit.setCampainCountry(adsUrl.getCampainCountry());
        audit.setFullUrl(adsUrl.getFullUrl());
        audit.setLandingUrl(adsUrl.getLandingUrl());
        audit.setLandingPageUrl(adsUrl.getLandingPageUrl());
        audit.setUrlSuffix(adsUrl.getUrlSuffix());
        audit.setPlatform(adsUrl.getPlatform());
        audit.setPlatformName(adsUrl.getPlatformName());
        audit.setRemark(adsUrl.getRemark());
        audit.setRemarks(adsUrl.getRemarks());
        audit.setCampaignOwner(adsUrl.getCampaignOwner());
        audit.setAdsOwner(adsUrl.getAdsOwner());
        audit.setAdsType(adsUrl.getAdsType());
        audit.setCampainId(adsUrl.getCampainId());
        audit.setSeqNumber(adsUrl.getSeqNumber());
        audit.setDisplayNumber(adsUrl.getDisplayNumber());
        audit.setDisplayTimes(adsUrl.getDisplayTimes());
        audit.setStatus(adsUrl.getStatus());
        audit.setCreateDate(adsUrl.getCreateDate());
        audit.setUpdateDate(adsUrl.getUpdateDate());
        audit.setVersion(adsUrl.getVersion());
        audit.setReversion(reversion);
        adsUrlAudRepository.save(audit);
    }

    private Long nextRevision(AdsUrl adsUrl) {
        return (adsUrl.getVersion() != null ? adsUrl.getVersion() : 0L) + 1L;
    }

    public record BulkUploadResult(int campaignCount, int rowCount, int deletedCount, int insertedCount) {
    }

    private record ExcelRowData(String campaignName, String campaignCountry, String platformName, String fullUrl, Long displayNumber, String remarks) {
    }
}
