package com.admire.cars.runner.db;

import liquibase.Scope;
import liquibase.command.CommandScope;
import liquibase.command.core.UpdateCommandStep;
import liquibase.command.core.helpers.DbUrlConnectionArgumentsCommandStep;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 校验花费台账迁移: 建表并用任务上已有的花费初始化 / verify the ledger migration creates the table and seeds existing cost
 */
class CampaignCostLedgerMigrationTest {

    private static final String CHANGELOG = "db/changelog/db.changelog-076-campaign-cost-ledger.xml";

    @Test
    void seedsLedgerFromNormalTaskCost() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:ledger_migration;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE ADS_NORMAL_INFO (ID BIGINT PRIMARY KEY, ADS_OWNER VARCHAR(32), "
                        + "CAMPAIN_NAME VARCHAR(32), LAST_REPORT_COST DECIMAL(18,4), COST_CURRENCY VARCHAR(8))");
                statement.execute("INSERT INTO ADS_NORMAL_INFO VALUES (11, 'owner', 'momcozy', 97.77, 'CNY')");
                statement.execute("INSERT INTO ADS_NORMAL_INFO VALUES (12, 'owner', 'nocost', NULL, NULL)");
                statement.execute("INSERT INTO ADS_NORMAL_INFO VALUES (3, 'owner', 'dup', 5, 'CNY')");
                statement.execute("INSERT INTO ADS_NORMAL_INFO VALUES (4, 'owner', 'dup', 8, 'CNY')");
                statement.execute("INSERT INTO ADS_NORMAL_INFO VALUES (5, 'other', 'momcozy', 1, 'USD')");
            }

            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            Scope.child(Scope.Attr.resourceAccessor.name(), new ClassLoaderResourceAccessor(), () ->
                    new CommandScope(UpdateCommandStep.COMMAND_NAME)
                            .addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, database)
                            .addArgumentValue(UpdateCommandStep.CHANGELOG_FILE_ARG, CHANGELOG)
                            .execute());

            Map<String, Object[]> rows = new LinkedHashMap<>();
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT ADS_OWNER, CAMPAIGN_KEY, CAMPAIGN_NAME, "
                         + "LIFETIME_COST, COST_CURRENCY, NORMAL_ADS_ID, CAMPAIGN_ID FROM ADS_CAMPAIGN_COST")) {
                while (resultSet.next()) {
                    rows.put(resultSet.getString(1) + "|" + resultSet.getString(2), new Object[]{
                            resultSet.getString(3), resultSet.getBigDecimal(4), resultSet.getString(5),
                            resultSet.getLong(6), resultSet.getString(7)});
                }
            }

            assertEquals(3, rows.size());
            Object[] momcozy = rows.get("owner|NAME:momcozy");
            assertEquals("momcozy", momcozy[0]);
            assertEquals(0, new BigDecimal("97.77").compareTo((BigDecimal) momcozy[1]));
            assertEquals("CNY", momcozy[2]);
            assertEquals(11L, momcozy[3]);
            assertEquals(null, momcozy[4]);
            // 同名任务取最大花费和最新任务 / duplicated names keep the max cost and the newest task id
            Object[] dup = rows.get("owner|NAME:dup");
            assertEquals(0, new BigDecimal("8").compareTo((BigDecimal) dup[1]));
            assertEquals(4L, dup[3]);
            assertEquals(0, BigDecimal.ONE.compareTo((BigDecimal) rows.get("other|NAME:momcozy")[1]));
        }
    }
}
