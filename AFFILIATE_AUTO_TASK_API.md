# AFFILIATE_AUTO_TASK CRUD API Documentation

## Overview
This document describes the CRUD operations for the AFFILIATE_AUTO_TASK entity with support for filtering and pagination.

## Database Schema

### Table: AFFILIATE_AUTO_TASK

| Column | Type | Nullable | Constraints | Description |
|--------|------|----------|-------------|-------------|
| ID | BIGINT | No | PRIMARY KEY, AUTO_INCREMENT | Unique identifier |
| AFFILIATE_NETWORK | VARCHAR(64) | No | NOT NULL | Name of the affiliate network |
| AUTO_TASK_TYPE | VARCHAR(64) | No | NOT NULL | Type of task: SYNC or TEST |
| REGION | VARCHAR(64) | No | NOT NULL | Geographic region (country) |
| TOTAL_COUNT | BIGINT | Yes | - | Total number of items processed |
| SUCCESS_COUNT | BIGINT | Yes | - | Number of successful items |
| FAILED_COUNT | BIGINT | Yes | - | Number of failed items |
| START_DATE | TIMESTAMP | Yes | - | Task start date and time |
| END_DATE | TIMESTAMP | Yes | - | Task end date and time |
| DURATION | BIGINT | Yes | - | Task duration in milliseconds |
| STATUS | VARCHAR(64) | No | NOT NULL | Task status: NOT_RUN, IN_PROGRESS, SUCCESS, FAILED |
| ADS_OWNER | VARCHAR(32) | No | FK(ADS_USER.USER_PHONE_NUMBER) | Task owner phone number |
| CREATE_DATE | TIMESTAMP | No | DEFAULT CURRENT_TIMESTAMP | Record creation timestamp |
| UPDATE_DATE | TIMESTAMP | Yes | - | Record last update timestamp |

### Indexes
- IDX_AAT_ADS_OWNER: AFFILIATE_AUTO_TASK(ADS_OWNER)
- IDX_AAT_AFFILIATE_NETWORK: AFFILIATE_AUTO_TASK(AFFILIATE_NETWORK)
- IDX_AAT_REGION: AFFILIATE_AUTO_TASK(REGION)
- IDX_AAT_STATUS: AFFILIATE_AUTO_TASK(STATUS)
- IDX_AAT_CREATE_DATE: AFFILIATE_AUTO_TASK(CREATE_DATE)

## API Endpoints

### 1. Create AFFILIATE_AUTO_TASK
**Endpoint:** `POST /api/affiliate-auto-task`

**Description:** Creates a new AFFILIATE_AUTO_TASK record.

**Request Body:**
```json
{
  "affiliateNetwork": "BonusArrive",
  "autoTaskType": "SYNC",
  "region": "US",
  "status": "NOT_RUN",
  "adsOwner": "13800000000"
}
```

**Request Parameters:**
- Headers: Must include valid authentication (userId in request context)

**Response (201 Created):**
```json
{
  "success": true,
  "message": "AFFILIATE_AUTO_TASK created successfully",
  "id": 1,
  "data": {
    "id": 1,
    "affiliateNetwork": "BonusArrive",
    "autoTaskType": "SYNC",
    "region": "US",
    "totalCount": 0,
    "successCount": 0,
    "failedCount": 0,
    "startDate": null,
    "endDate": null,
    "duration": null,
    "status": "NOT_RUN",
    "adsOwner": "13800000000",
    "createDate": "2026-08-06T16:20:14",
    "updateDate": null
  }
}
```

**Error Response (400 Bad Request):**
```json
{
  "success": false,
  "message": "Error message describing what went wrong"
}
```

---

### 2. Get AFFILIATE_AUTO_TASK by ID
**Endpoint:** `GET /api/affiliate-auto-task/{id}`

**Description:** Retrieves a specific AFFILIATE_AUTO_TASK record by ID.

**Path Parameters:**
- `id` (Long, required): The ID of the task to retrieve

**Response (200 OK):**
```json
{
  "id": 1,
  "affiliateNetwork": "BonusArrive",
  "autoTaskType": "SYNC",
  "region": "US",
  "totalCount": 100,
  "successCount": 95,
  "failedCount": 5,
  "startDate": "2026-08-06T16:20:00",
  "endDate": "2026-08-06T16:22:00",
  "duration": 120000,
  "status": "SUCCESS",
  "adsOwner": "13800000000",
  "createDate": "2026-08-06T16:20:14",
  "updateDate": "2026-08-06T16:22:14"
}
```

**Error Response (404 Not Found):**
Returns 404 status code if record not found or user lacks permissions.

---

### 3. Search AFFILIATE_AUTO_TASK (with Pagination & Filtering)
**Endpoint:** `GET /api/affiliate-auto-task`

**Description:** Searches AFFILIATE_AUTO_TASK records with support for filtering and pagination.

**Query Parameters:**
- `adsOwner` (String, optional): Filter by ADS_OWNER. Admin users can filter by any owner; non-admin users see only their own records.
- `affiliateNetwork` (String, optional): Filter by AFFILIATE_NETWORK (partial match, case-insensitive)
- `region` (String, optional): Filter by REGION (partial match, case-insensitive)
- `status` (String, optional): Filter by STATUS (exact match, case-insensitive). Values: NOT_RUN, IN_PROGRESS, SUCCESS, FAILED
- `page` (Integer, optional, default: 0): Zero-based page index
- `size` (Integer, optional, default: 10): Number of records per page

**Example Request:**
```
GET /api/affiliate-auto-task?adsOwner=13800000000&status=SUCCESS&page=0&size=20
```

**Response (200 OK):**
```json
{
  "content": [
    {
      "id": 1,
      "affiliateNetwork": "BonusArrive",
      "autoTaskType": "SYNC",
      "region": "US",
      "totalCount": 100,
      "successCount": 95,
      "failedCount": 5,
      "startDate": "2026-08-06T16:20:00",
      "endDate": "2026-08-06T16:22:00",
      "duration": 120000,
      "status": "SUCCESS",
      "adsOwner": "13800000000",
      "createDate": "2026-08-06T16:20:14",
      "updateDate": "2026-08-06T16:22:14"
    }
  ],
  "pageable": {
    "sort": {
      "empty": false,
      "sorted": true,
      "unsorted": false
    },
    "offset": 0,
    "pageNumber": 0,
    "pageSize": 20,
    "paged": true,
    "unpaged": false
  },
  "totalPages": 1,
  "totalElements": 1,
  "last": true,
  "size": 20,
  "number": 0,
  "sort": {
    "empty": false,
    "sorted": true,
    "unsorted": false
  },
  "first": true,
  "numberOfElements": 1,
  "empty": false
}
```

**Filtering Logic:**
- Multiple filters can be combined (AND logic)
- Filters for `affiliateNetwork` and `region` use case-insensitive partial matching (LIKE)
- Filter for `status` uses exact matching (case-insensitive)
- Non-admin users can only see their own records (filtered by their ADS_OWNER)
- Admin users can filter by any ADS_OWNER

**Sorting:**
- Default sort: CREATE_DATE (DESC), then ID (DESC)

---

### 4. Update AFFILIATE_AUTO_TASK
**Endpoint:** `PUT /api/affiliate-auto-task/{id}`

**Description:** Updates an existing AFFILIATE_AUTO_TASK record.

**Path Parameters:**
- `id` (Long, required): The ID of the task to update

**Request Body (All fields optional):**
```json
{
  "affiliateNetwork": "BonusArrive",
  "autoTaskType": "SYNC",
  "region": "US",
  "totalCount": 100,
  "successCount": 95,
  "failedCount": 5,
  "startDate": "2026-08-06T16:20:00",
  "endDate": "2026-08-06T16:22:00",
  "duration": 120000,
  "status": "SUCCESS",
  "adsOwner": "13800000000"
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "AFFILIATE_AUTO_TASK updated successfully",
  "data": {
    "id": 1,
    "affiliateNetwork": "BonusArrive",
    "autoTaskType": "SYNC",
    "region": "US",
    "totalCount": 100,
    "successCount": 95,
    "failedCount": 5,
    "startDate": "2026-08-06T16:20:00",
    "endDate": "2026-08-06T16:22:00",
    "duration": 120000,
    "status": "SUCCESS",
    "adsOwner": "13800000000",
    "createDate": "2026-08-06T16:20:14",
    "updateDate": "2026-08-06T16:25:30"
  }
}
```

**Error Response (400 Bad Request):**
```json
{
  "success": false,
  "message": "Error message describing what went wrong"
}
```

**Notes:**
- Only the fields provided will be updated (partial update)
- Non-admin users cannot change the `adsOwner` field
- All field validations are applied during update

---

### 5. Delete AFFILIATE_AUTO_TASK
**Endpoint:** `DELETE /api/affiliate-auto-task/{id}`

**Description:** Deletes an AFFILIATE_AUTO_TASK record.

**Path Parameters:**
- `id` (Long, required): The ID of the task to delete

**Response (200 OK):**
```json
{
  "success": true,
  "message": "AFFILIATE_AUTO_TASK deleted successfully"
}
```

**Error Response (400 Bad Request):**
```json
{
  "success": false,
  "message": "Error message describing what went wrong"
}
```

---

## Validation Rules

### Field Validations
1. **affiliateNetwork**: Required, max 64 characters
2. **autoTaskType**: Required, max 64 characters, must be one of: SYNC, TEST
3. **region**: Required, max 64 characters
4. **status**: Required, max 64 characters, must be one of: NOT_RUN, IN_PROGRESS, SUCCESS, FAILED
5. **adsOwner**: Required, max 32 characters, must exist in ADS_USER table
6. **totalCount, successCount, failedCount**: Must be non-negative

### Count Field Defaults
- If not provided during creation, `totalCount`, `successCount`, and `failedCount` default to 0

### Timestamp Handling
- `createDate`: Set to current timestamp when record is created, cannot be modified
- `updateDate`: Set to current timestamp when record is updated

---

## Authorization & Access Control

### Admin Users
- Can create, read, update, and delete any AFFILIATE_AUTO_TASK record
- Can filter tasks by any ADS_OWNER value
- Can change the ADS_OWNER field when updating

### Non-Admin Users
- Can only create tasks with themselves as ADS_OWNER
- Can only view their own tasks
- Can only update their own tasks
- Can only delete their own tasks
- Cannot change the ADS_OWNER field

---

## Error Codes

| HTTP Status | Error Message | Description |
|-------------|---------------|-------------|
| 400 | AFFILIATE_AUTO_TASK is required | Request body is null |
| 400 | affiliateNetwork is required | Missing required field |
| 400 | autoTaskType is required | Missing required field |
| 400 | region is required | Missing required field |
| 400 | adsOwner is required | Missing required field |
| 400 | status is required | Missing required field |
| 400 | ADS_USER not found by phone number | adsOwner phone number doesn't exist in system |
| 400 | Unauthorized: adsOwner must match current user | Non-admin user trying to create task for another user |
| 400 | autoTaskType must be one of: SYNC, TEST | Invalid autoTaskType value |
| 400 | status must be one of: NOT_RUN, IN_PROGRESS, SUCCESS, FAILED | Invalid status value |
| 400 | count fields must be non-negative | Negative value in totalCount, successCount, or failedCount |
| 404 | AFFILIATE_AUTO_TASK not found: {id} | Record with given ID doesn't exist |
| 400 | Unauthorized: you can only modify your own auto tasks | Non-admin user trying to update/delete another user's task |

---

## Usage Examples

### Example 1: Create a new sync task
```bash
curl -X POST http://localhost:8080/api/affiliate-auto-task \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "affiliateNetwork": "BonusArrive",
    "autoTaskType": "SYNC",
    "region": "US",
    "status": "NOT_RUN",
    "adsOwner": "13800000000"
  }'
```

### Example 2: Search tasks by status and region with pagination
```bash
curl -X GET 'http://localhost:8080/api/affiliate-auto-task?status=SUCCESS&region=US&page=0&size=10' \
  -H "Authorization: Bearer <token>"
```

### Example 3: Update task status
```bash
curl -X PUT http://localhost:8080/api/affiliate-auto-task/1 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "status": "IN_PROGRESS",
    "startDate": "2026-08-06T16:20:00"
  }'
```

### Example 4: Delete a task
```bash
curl -X DELETE http://localhost:8080/api/affiliate-auto-task/1 \
  -H "Authorization: Bearer <token>"
```

---

## Notes

1. **Database Migration**: The table is created using Liquibase migration file `db.changelog-036-affiliate-auto-task-table.xml`
2. **Pagination**: Results are sorted by `CREATE_DATE` (descending) and then by `ID` (descending)
3. **Case Sensitivity**: All enum-like fields (autoTaskType, status) are normalized to uppercase and stored as-is
4. **Partial Matching**: Filters for `affiliateNetwork` and `region` support partial/wildcard matching
5. **Date Filtering**: Use the pagination and sorting features; specific date range filtering can be extended if needed
