# Database Naming Convention

## Table Naming Pattern

All tables must follow **UPPERCASE** naming convention:

### Application Tables: `ADS_*`
- **ADS_USER** - User management
- **ADS_USER_AUD** - User audit trail
- **ADS_PLATFORM** - ADS platform master data
- **ADS_*** - Other application tables

### Scheduler Tables: `QRTZ_*`
- **QRTZ_JOB_DETAILS** - Quartz job definitions
- **QRTZ_TRIGGERS** - Quartz triggers
- **QRTZ_*** - Other Quartz tables

## Column Naming Pattern

Columns should be:
- **UPPERCASE** with underscores for word separation
- Prefixed with table name for audit/related tables

Examples:
```
ADS_USER table:
  ID                  (Primary key)
  USER_NAME           (Unique username)
  USER_EMAIL          (Unique email)
  USER_PHONE_NUMBER   (Unique phone)
  USER_PASSWORD       (Password hash)
  STATUS              (ENABLED/DISABLED)
  CREATE_DATE         (Creation timestamp)
  UPDATE_DATE         (Last update timestamp)

ADS_USER_AUD table:
  AUD_ID              (Audit record ID)
  USER_ID             (Referenced user)
  OPERATION           (CREATE/UPDATE/DELETE/ENABLE/DISABLE)
  OPERATION_DATE      (When operation occurred)
  OLD_VALUE           (Previous values)
  NEW_VALUE           (New values)
  OPERATOR            (Who performed operation)
```

## Java Entity Naming

Entity field names use camelCase, but map to UPPERCASE column names:

```java
@Entity
@Table(name = "ADS_USER")
public class User {
    @Column(name = "ID")
    private Long id;
    
    @Column(name = "USER_NAME")
    private String userName;
    
    @Column(name = "CREATE_DATE")
    private LocalDateTime createDate;
}
```

## Index Naming Pattern

Indexes should be named: `IDX_<TABLE>_<COLUMNS>`

Examples:
- `IDX_ADS_USER_AUD_USER_ID`
- `IDX_ADS_USER_AUD_OPERATION_DATE`

## Foreign Key Naming Pattern

Foreign keys should be named: `FK_<TABLE>_<COLUMN>_<REFERENCED_TABLE>`

Example:
- `FK_ADS_USER_AUD_USER_ID` (ADS_USER_AUD.USER_ID references ADS_USER)

## Migration Checklist

When creating new tables:
- [ ] Table name in UPPERCASE
- [ ] Column names in UPPERCASE with underscores
- [ ] Primary key: `ID`
- [ ] Timestamps: `CREATE_DATE`, `UPDATE_DATE`
- [ ] Status field if applicable: `STATUS`
- [ ] Use `ADS_` prefix for application tables
- [ ] Add proper indexes for foreign keys
- [ ] Include audit table if needed: `<TABLE>_AUD`
- [ ] Update this document

## Examples

### Good ✅
```xml
<createTable tableName="ADS_PRODUCT">
    <column name="ID" type="BIGINT" autoIncrement="true">
        <constraints primaryKey="true" nullable="false"/>
    </column>
    <column name="PRODUCT_NAME" type="VARCHAR(255)">
        <constraints nullable="false"/>
    </column>
    <column name="PRODUCT_CODE" type="VARCHAR(50)">
        <constraints nullable="false" unique="true"/>
    </column>
    <column name="STATUS" type="VARCHAR(20)">
        <constraints nullable="false"/>
    </column>
    <column name="CREATE_DATE" type="TIMESTAMP">
        <constraints nullable="false"/>
    </column>
    <column name="UPDATE_DATE" type="TIMESTAMP"/>
</createTable>
```

### Bad ❌
```xml
<!-- Don't use lowercase -->
<createTable tableName="ads_product">
    <column name="product_id" type="BIGINT">
    <column name="productName" type="VARCHAR(255)">
    <column name="createdAt" type="TIMESTAMP">
</createTable>
```

## Why This Pattern?

1. **Consistency** - All tables follow same pattern
2. **Readability** - UPPERCASE stands out in SQL queries
3. **Organization** - `ADS_` prefix groups application tables
4. **Searchability** - Easy to find all application vs scheduler tables
5. **Scalability** - Clear pattern for new developers to follow
