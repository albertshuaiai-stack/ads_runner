# Liquibase Database Migration Guide

## Overview
This project uses **Liquibase** for database version control and schema management. All database changes are tracked and can be applied/reapplied as needed.

## Key Features

### Liquibase Tracking Tables
Liquibase automatically creates two tracking tables when it first runs:
- **DATABASECHANGELOG** - Records all applied changesets
- **DATABASECHANGELOGLOCK** - Prevents concurrent migrations

These tables are created automatically, no manual setup needed.

### Changeset Configuration
Each changeset has a unique **id** and **author** combination to track changes:
```xml
<changeSet id="unique-id" author="copilot" runOnChange="true">
```

### Rerunnable Scripts
Changesets marked with `runOnChange="true"` will be re-executed if their content changes:
```xml
<changeSet id="user-004-insert-admin-user" author="copilot" runOnChange="true">
    <preConditions onFail="MARK_RAN">
        <rowCount tableName="ADS_USER" expectedRows="0"/>
    </preConditions>
    ...
</changeSet>
```

### Preconditions
Preconditions check if a changeset should run:
- `onFail="MARK_RAN"` - Skip the changeset but mark it as executed
- `onFail="HALT"` - Stop migration if precondition fails
- `onFail="CONTINUE"` - Continue migration

## Application Properties

```properties
# Liquibase configuration
spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.xml
spring.liquibase.enabled=true          # Enable Liquibase
spring.liquibase.drop-first=false      # Do NOT drop schema before running
```

## Changeset Files Structure

```
src/main/resources/db/changelog/
├── db.changelog-master.xml              # Master changelog (includes all others)
├── db.changelog-001-quartz-tables.xml   # Quartz scheduler tables
└── db.changelog-002-user-tables.xml     # User and audit tables
```

## Running Migrations

### Automatic (on app startup)
Migrations run automatically when Spring Boot starts. Liquibase will:
1. Check DATABASECHANGELOG table
2. Compare with changesets
3. Execute only new/modified changesets
4. Update tracking tables

### Manual (via Maven)
```bash
mvn liquibase:update
mvn liquibase:status
mvn liquibase:history
```

## Adding New Changesets

### Step 1: Create new changeset file
```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog ...>
    <changeSet id="feature-001-create-table" author="your-name">
        <createTable tableName="NEW_TABLE">
            ...
        </createTable>
    </changeSet>
</databaseChangeLog>
```

### Step 2: Include in master changelog
```xml
<include file="db/changelog/db.changelog-003-feature.xml"/>
```

### Step 3: Run app or execute migration
The new changesets will be applied automatically.

## Important Notes

1. **Never Modify Executed Changesets**
   - Once a changeset is executed, don't change its `id` or `author`
   - Create a new changeset for modifications

2. **Use Preconditions**
   - Prevent errors from duplicate data
   - Check if table/column exists before operations

3. **Idempotent Scripts**
   - Use `IF NOT EXISTS` or preconditions
   - Ensures reruns don't cause errors

4. **Test Migrations**
   - Always test in dev/staging first
   - Verify DATABASECHANGELOG entries match your changesets

## Current Changesets

| ID | Author | Table | Purpose |
|---|---|---|---|
| quartz-* | copilot | QRTZ_* | Quartz scheduler tables |
| user-001 | copilot | ADS_USER | User management table |
| user-002 | copilot | ADS_USER_AUD | User audit trail |
| user-003 | copilot | ADS_USER_AUD | Audit indexes |
| user-004 | copilot | ADS_USER | Initialize admin user |

## Troubleshooting

### Error: "Unexpected error executing SQL"
- Check database connection
- Verify user permissions
- Check DATABASECHANGELOG for conflicts

### Error: "Duplicate entry"
- Changeset may have run partially before
- Check preconditions
- Clear DATABASECHANGELOG if needed (be careful!)

### Want to rerun a changeset?
- Mark it with `runOnChange="true"`
- Modify the changeset content
- Liquibase will detect the change and rerun

## Rollback

Liquibase does NOT automatically rollback. To support rollbacks, add:
```xml
<changeSet id="example" author="copilot">
    <createTable tableName="EXAMPLE">
        ...
    </createTable>
    <rollback>
        <dropTable tableName="EXAMPLE"/>
    </rollback>
</changeSet>
```

## References
- [Liquibase Documentation](https://docs.liquibase.com)
- [MySQL Dialect Support](https://docs.liquibase.com/workflows/liquibase-community/using-liquibase-community/supported-databases/mysql.html)
- [Preconditions](https://docs.liquibase.com/concepts/changelogs/preconditions.html)
