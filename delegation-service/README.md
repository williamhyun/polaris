<!--
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at
 
   http://www.apache.org/licenses/LICENSE-2.0
 
  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
-->

# Polaris Delegation Service

This module contains the Polaris Delegation Service, which handles long-running tasks delegated from the main Polaris catalog service.

## 🎯 Overview

The Delegation Service is designed to:
- Accept task execution requests from the main Polaris catalog
- Execute long-running operations (e.g., table purging) **synchronously**
- Maintain low-latency performance in the main catalog service
- Provide scalable task execution capabilities

## 🏗️ Architecture

The service consists of:
- **API Layer**: REST endpoints for task submission (`DelegationApi`)
- **Service Layer**: Task execution logic and orchestration (`TaskExecutionService`)
- **Storage Layer**: Data file cleanup operations (`StorageFileManager`)

## 🚀 E2E POC Setup

### Prerequisites
- Java 21
- Docker & Docker Compose
- Gradle

### 1. Build the Service
```bash
cd delegation-service
./gradlew build
```

### 2. Run with Docker Compose
```bash
# Build and start the delegation service
docker-compose up --build

# The service will be available at:
# - Health check: http://localhost:8080/health
# - API endpoint: http://localhost:8080/api/v1/tasks/execute/synchronous
```

### 3. Test the Integration

#### Start Delegation Service
```bash
# Terminal 1: Start delegation service
docker-compose up delegation-service

# Wait for: "🚀 Polaris Delegation Service started successfully!"
```

#### Test Polaris Integration
```bash
# Terminal 2: Configure Polaris to use delegation service
export POLARIS_DELEGATION_ENABLED=true
export POLARIS_DELEGATION_BASE_URL=http://localhost:8080
export POLARIS_DELEGATION_TIMEOUT_SECONDS=30

# Run Polaris with delegation enabled
# (This would be your normal Polaris startup command)
```

#### Execute DROP TABLE WITH PURGE
```bash
# Terminal 3: Execute a table purge operation
# This will now go through the delegation service

# In your SQL client or API call:
DROP TABLE test_table PURGE;
```

### 4. Manual API Testing
```bash
# Test health endpoint
curl http://localhost:8080/health

# Test delegation API
curl -X POST http://localhost:8080/api/v1/tasks/execute/synchronous \
  -H 'Content-Type: application/json' \
  -d '{
    "common_payload": {
      "task_type": "PURGE_TABLE",
      "request_timestamp_utc": "2024-01-01T00:00:00Z",
      "realm_identifier": "test-realm"
    },
    "operation_parameters": {
      "task_type": "PURGE_TABLE",
      "table_identity": {
        "catalog_name": "test-catalog",
        "namespace_levels": ["test-namespace"],
        "table_name": "test-table"
      },
      "properties": {
        "POLARIS_STORAGE_LOCATION": "s3://test-bucket/test-table/"
      }
    }
  }'
```

## 📋 Expected POC Flow

1. **User**: `DROP TABLE test_table PURGE`
2. **Polaris**: Checks delegation configuration
3. **Delegation**: Polaris sends HTTP POST to delegation service
4. **Delegation Service**: Executes data file cleanup (simulated)
5. **Delegation Service**: Returns success response
6. **Polaris**: Receives success → removes metadata from catalog
7. **User**: Gets success response

## 🔧 Configuration

### Environment Variables
- `POLARIS_DELEGATION_ENABLED`: Enable/disable delegation (default: false)
- `POLARIS_DELEGATION_BASE_URL`: Delegation service URL (default: http://localhost:8080)
- `POLARIS_DELEGATION_TIMEOUT_SECONDS`: Request timeout (default: 30)
- `POLARIS_DELEGATION_PORT`: Service port (default: 8080)
- `POLARIS_DELEGATION_HOST`: Service host (default: localhost)

### System Properties
- `polaris.delegation.enabled`: Same as POLARIS_DELEGATION_ENABLED
- `polaris.delegation.baseUrl`: Same as POLARIS_DELEGATION_BASE_URL
- `polaris.delegation.timeoutSeconds`: Same as POLARIS_DELEGATION_TIMEOUT_SECONDS

## 🏃 Running Standalone

```bash
# Build the service
./gradlew build

# Run directly
java -jar build/libs/delegation-service-*.jar

# Or with configuration
export POLARIS_DELEGATION_PORT=8080
java -jar build/libs/delegation-service-*.jar
```

## 📝 API Documentation

### POST /api/v1/tasks/execute/synchronous
Executes a task synchronously and returns the result.

**Request Body:**
```json
{
  "common_payload": {
    "task_type": "PURGE_TABLE",
    "request_timestamp_utc": "2024-01-01T00:00:00Z",
    "realm_identifier": "test-realm"
  },
  "operation_parameters": {
    "task_type": "PURGE_TABLE",
    "table_identity": {
      "catalog_name": "test-catalog",
      "namespace_levels": ["test-namespace"],
      "table_name": "test-table"
    },
    "properties": {
      "POLARIS_STORAGE_LOCATION": "s3://test-bucket/test-table/"
    }
  }
}
```

**Response:**
```json
{
  "status": "success",
  "result_summary": "Successfully cleaned up 18 data files in 2000 ms"
}
```

### GET /health
Health check endpoint.

**Response:**
```json
{
  "status": "healthy",
  "service": "polaris-delegation-service"
}
```

## 🎯 POC Validation

The POC demonstrates:
- ✅ **Polaris Integration**: Modified IcebergCatalog calls delegation service
- ✅ **Synchronous Execution**: Blocking HTTP calls ensure proper timing
- ✅ **Data/Metadata Separation**: Delegation handles data, Polaris handles metadata
- ✅ **Configuration Management**: External configuration via environment variables
- ✅ **Error Handling**: Graceful fallback to local execution
- ✅ **Docker Deployment**: Easy containerized deployment
- ✅ **API Contracts**: Well-defined REST API with proper serialization

## 🚦 Current Status

- **Integration**: ✅ Complete
- **API Framework**: ✅ Complete
- **Task Execution**: ✅ Simulated (POC)
- **Storage Operations**: ✅ Simulated (POC)
- **Docker Setup**: ✅ Complete
- **Documentation**: ✅ Complete

For production deployment, the simulated storage operations would be replaced with actual Iceberg file cleanup logic.
