package com.admire.cars.runner.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

// SHIFT_LINK_STAT 启动自愈：合并重复分组并确保唯一索引存在
// startup self-heal for SHIFT_LINK_STAT: merge duplicate groups and ensure the unique index exists
@Slf4j
@Component
public class ShiftLinkStatMaintenance implements ApplicationRunner {

    private static final String UNIQUE_INDEX_NAME = "UK_SHIFT_LINK_STAT_GROUP";

    private final JdbcTemplate jdbcTemplate;

    public ShiftLinkStatMaintenance(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Integer tableCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'SHIFT_LINK_STAT'",
                    Integer.class);
            if (tableCount == null || tableCount == 0) {
                return;
            }

            // 合并重复分组：只统计不同的 URL，重复行不计数
            // merge duplicate groups: count distinct urls only, duplicated rows are not counted
            int merged = jdbcTemplate.update("""
                    UPDATE SHIFT_LINK_STAT a
                    JOIN (SELECT ADS_OWNER, USER_NAME, PLATFORM_NAME, ADS_NAME,
                                 MIN(ID) AS KEEP_ID,
                                 COUNT(DISTINCT SAMPLE_URL) AS UNIQUE_COUNT,
                                 MAX(SAMPLE_URL) AS SAMPLE
                          FROM SHIFT_LINK_STAT
                          GROUP BY ADS_OWNER, USER_NAME, PLATFORM_NAME, ADS_NAME
                          HAVING COUNT(*) > 1) b
                      ON a.ADS_OWNER = b.ADS_OWNER AND a.USER_NAME = b.USER_NAME
                     AND a.PLATFORM_NAME = b.PLATFORM_NAME AND a.ADS_NAME = b.ADS_NAME
                     AND a.ID = b.KEEP_ID
                    SET a.LINK_COUNT = b.UNIQUE_COUNT, a.SAMPLE_URL = b.SAMPLE
                    """);
            int deleted = jdbcTemplate.update("""
                    DELETE a FROM SHIFT_LINK_STAT a
                    JOIN (SELECT ADS_OWNER, USER_NAME, PLATFORM_NAME, ADS_NAME, MIN(ID) AS KEEP_ID
                          FROM SHIFT_LINK_STAT
                          GROUP BY ADS_OWNER, USER_NAME, PLATFORM_NAME, ADS_NAME
                          HAVING COUNT(*) > 1) b
                      ON a.ADS_OWNER = b.ADS_OWNER AND a.USER_NAME = b.USER_NAME
                     AND a.PLATFORM_NAME = b.PLATFORM_NAME AND a.ADS_NAME = b.ADS_NAME
                     AND a.ID <> b.KEEP_ID
                    """);
            if (merged > 0 || deleted > 0) {
                log.info("SHIFT_LINK_STAT dedupe done merged={} deleted={}", merged, deleted);
            }

            // 缺唯一索引则补上，保证 upsert 去重生效 / add the unique index when missing so upsert dedupes
            List<String> indexes = jdbcTemplate.queryForList(
                    "SELECT INDEX_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'SHIFT_LINK_STAT' AND INDEX_NAME = ?",
                    String.class, UNIQUE_INDEX_NAME);
            if (indexes.isEmpty()) {
                jdbcTemplate.execute("ALTER TABLE SHIFT_LINK_STAT ADD UNIQUE INDEX " + UNIQUE_INDEX_NAME
                        + " (ADS_OWNER, USER_NAME, PLATFORM_NAME, ADS_NAME)");
                log.info("SHIFT_LINK_STAT unique index {} created", UNIQUE_INDEX_NAME);
            }

            // 去重表存在性 + 唯一索引自检 / ensure the seen-url table and its unique index exist
            Integer seenTableCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'SHIFT_LINK_SEEN_URL'",
                    Integer.class);
            if (seenTableCount != null && seenTableCount > 0) {
                List<String> seenIndexes = jdbcTemplate.queryForList(
                        "SELECT INDEX_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'SHIFT_LINK_SEEN_URL' AND INDEX_NAME = ?",
                        String.class, "UK_SHIFT_LINK_SEEN_URL");
                if (seenIndexes.isEmpty()) {
                    jdbcTemplate.execute("ALTER TABLE SHIFT_LINK_SEEN_URL ADD UNIQUE INDEX UK_SHIFT_LINK_SEEN_URL (ADS_OWNER, URL_HASH)");
                }
                // 回填现存链接的 URL 哈希 / backfill hashes of existing links
                int seeded = jdbcTemplate.update("""
                        INSERT IGNORE INTO SHIFT_LINK_SEEN_URL (ADS_OWNER, URL_HASH, CREATE_DATE)
                        SELECT DISTINCT ADS_OWNER, SHA2(FULL_URL, 256), CURRENT_TIMESTAMP
                        FROM SHIFT_LINK WHERE FULL_URL IS NOT NULL
                        """);
                if (seeded > 0) {
                    log.info("SHIFT_LINK_SEEN_URL seeded count={}", seeded);
                }
            }
        } catch (Exception e) {
            // 自愈失败不影响启动 / self-heal failure must not break startup
            log.warn("SHIFT_LINK_STAT maintenance failed: {}", e.getMessage());
        }
    }
}
