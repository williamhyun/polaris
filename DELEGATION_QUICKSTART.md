DELEGATION_QUICKSTART.md# Polaris Delegation Service Quickstart Guide

This guide demonstrates how to set up and test the Polaris delegation service integration following the official Polaris quickstart pattern.

## Prerequisites

1. Java 11 or higher
2. Gradle
3. `jq` command-line tool for JSON processing
4. `curl` for making HTTP requests

## Architecture Overview

The delegation service acts as an external service that:
- Authenticates with Polaris using OAuth2 client credentials
- Receives table purge requests from external systems
- Uses Polaris REST API to load table metadata and credentials
- Performs table cleanup operations using Polaris-provided storage access

## Quick Start

### Step 1: Build the Project

```bash
# Build Polaris server
./gradlew :polaris-quarkus-service:build

# Build delegation service
./gradlew :polaris-delegation-service:build
```

### Step 2: Start Polaris Server

There are two ways to start Polaris:

#### Option A: Using Gradle (Recommended for Testing)

```bash
./gradlew :polaris-quarkus-service:run
```

This starts Polaris with default test credentials:
- **clientId**: `root`
- **clientSecret**: `test`

#### Option B: Using the JAR directly

```bash
cd runtime/server
java -jar build/quarkus-app/quarkus-run.jar
```

When started this way, Polaris generates bootstrap credentials that are printed to the console:
```
realm: POLARIS root principal credentials: bae6c20d828e752b:e7889937d824da9d4085d5e3a0ae9b66
```

### Step 3: Start Delegation Service

In a new terminal:

```bash
cd delegation-service
./gradlew run
```

The delegation service will start on port 8282.

### Step 4: Run the Test Script

#### For Gradle Setup (with default credentials):

```bash
chmod +x test-gradle-quickstart.sh
./test-gradle-quickstart.sh
```

#### For Complete Test (auto-detects credentials):

```bash
chmod +x test-delegation-quickstart.sh
./test-delegation-quickstart.sh
```

The test script will:
1. Detect which credentials to use (bootstrap or default)
2. Create a service principal for the delegation service
3. Create a test catalog with FILE storage
4. Set up proper roles and permissions
5. Create a test namespace and table
6. Test the delegation service integration

## Credential Management

### Default Test Credentials (Gradle)

When starting Polaris with `./gradlew :polaris-quarkus-service:run`, use:
- **clientId**: `root`
- **clientSecret**: `test`

### Bootstrap Credentials (Direct JAR)

When starting Polaris directly with the JAR, it generates new credentials on each startup. The test script automatically detects these from the startup output.

### Manual Authentication

To manually get an admin token:

```bash
# With default test credentials
curl -X POST "http://localhost:8181/api/catalog/v1/oauth/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=root&client_secret=test&scope=PRINCIPAL_ROLE:ALL"

# With bootstrap credentials
curl -X POST "http://localhost:8181/api/catalog/v1/oauth/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=<BOOTSTRAP_ID>&client_secret=<BOOTSTRAP_SECRET>&scope=PRINCIPAL_ROLE:ALL"
```

## Manual Testing

If you prefer to test manually, here are the key steps:

### 1. Get Admin Token

```bash
# Set credentials based on your setup
ADMIN_CLIENT_ID="root"
ADMIN_CLIENT_SECRET="test"

# Get token
ADMIN_TOKEN=$(curl -s -X POST "http://localhost:8181/api/catalog/v1/oauth/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=$ADMIN_CLIENT_ID&client_secret=$ADMIN_CLIENT_SECRET&scope=PRINCIPAL_ROLE:ALL" \
  | jq -r '.access_token')
```

### 2. Create Service Principal

```bash
curl -X POST "http://localhost:8181/api/management/v1/principals" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "principal": {
      "name": "delegation_service",
      "type": "SERVICE"
    }
  }'
```

### 3. Create Catalog

```bash
curl -X POST "http://localhost:8181/api/management/v1/catalogs" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "catalog": {
      "name": "test_catalog",
      "type": "INTERNAL",
      "properties": {
        "default-base-location": "file:///tmp/polaris-test-data"
      },
      "storageConfigInfo": {
        "storageType": "FILE",
        "allowedLocations": ["file:///tmp/polaris-test-data"]
      }
    }
  }'
```

### 4. Test Table Purge

```bash
curl -X POST "http://localhost:8282/api/v1/tasks/execute/synchronous" \
  -H "Content-Type: application/json" \
  -d '{
    "operation_type": "TABLE_PURGE",
    "request_timestamp_utc": "2024-01-15T10:00:00Z",
    "realm_identifier": "POLARIS",
    "operation_parameters": {
      "table_purge_parameters": {
        "table_identity": {
          "catalog_name": "test_catalog",
          "namespace_levels": ["test_namespace"],
          "table_name": "test_table"
        },
        "properties": {}
      }
    }
  }'
```

## API Endpoints

### Polaris Management API
- **Base URL**: `http://localhost:8181/api/management/v1`
- **Principals**: `/principals`
- **Catalogs**: `/catalogs`
- **Catalog Roles**: `/catalogs/{catalog}/catalog-roles`
- **Principal Roles**: `/principal-roles`

### Polaris Catalog API (Iceberg REST)
- **Base URL**: `http://localhost:8181/api/catalog/v1`
- **OAuth Token**: `/oauth/tokens`
- **Namespaces**: `/namespaces`
- **Tables**: `/namespaces/{namespace}/tables`

### Delegation Service API
- **Base URL**: `http://localhost:8282/api/v1`
- **Execute Task**: `/tasks/execute/synchronous`
- **Health Check**: `/health`

## Troubleshooting

### Authentication Issues

If you get 401 errors:
1. **For Gradle setup**: Ensure you're using `root`/`test` credentials
2. **For direct JAR**: Check the console output for the generated bootstrap credentials
3. Ensure the token hasn't expired (tokens are valid for a limited time)

### FILE Storage Not Enabled

If you get errors about FILE storage:
1. Ensure Polaris is started with the correct configuration
2. The Gradle setup (`./gradlew :polaris-quarkus-service:run`) has FILE storage enabled by default
3. For manual setup, set the environment variables as shown in the scripts

### Catalog Creation Issues

If catalog creation fails:
1. Check that the catalog doesn't already exist
2. Ensure the storage location exists and is accessible
3. Verify FILE storage type is enabled

### Permission Issues

If you get 403 errors:
1. Ensure all roles are properly created and assigned
2. Check that grants have been applied to catalog roles
3. Verify the principal role is assigned to the principal

## Configuration Details

### Polaris Configuration

Key environment variables for manual setup:
- `POLARIS_FEATURES_SUPPORTED_CATALOG_STORAGE_TYPES`: Must include "FILE"
- `POLARIS_FEATURES_ALLOW_INSECURE_STORAGE_TYPES`: Set to true for FILE storage
- `POLARIS_PERSISTENCE_TYPE`: Set to "in-memory" for testing

### Delegation Service Configuration

The delegation service reads configuration from:
- `POLARIS_URI`: Polaris server URL (default: http://localhost:8181)
- `DELEGATION_PORT`: Service port (default: 8282)

## Next Steps

1. **Production Setup**: For production, use proper persistence and secure storage
2. **Add Authentication**: Implement proper authentication for the delegation service
3. **Monitoring**: Add metrics and logging for production use
4. **Error Handling**: Enhance error handling and retry logic

## References

- [Polaris Documentation](https://polaris.apache.org/)
- [Iceberg REST Catalog API](https://iceberg.apache.org/docs/latest/rest-api/)
- [Polaris Quickstart Guide](https://polaris.apache.org/getting-started/quickstart/) 