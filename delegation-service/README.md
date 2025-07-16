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

## Overview

The Polaris Delegation Service handles table cleanup operations (such as `DROP TABLE ... PURGE`) by authenticating with Polaris and using its `loadTable()` API to access table metadata and storage credentials. This approach provides better security and consistency compared to direct storage access.

## Quick Start

### Prerequisites
- Java 11 or higher
- `jq` command-line JSON processor
- `curl` command-line tool
- `lsof` command (for port checking)

### Setup Steps

1. **Start Polaris with delegation enabled:**
   ```bash
   cd delegation-service
   ./start-polaris-with-delegation.sh
   ```

2. **Set up delegation service principal** (in another terminal):
   ```bash
   cd delegation-service
   ./setup-delegation-principal.sh
   ```

3. **Start the delegation service** (in another terminal):
   ```bash
   cd delegation-service  
   ./start-delegation-service.sh
   ```

4. **Test the complete integration:**
   ```bash
   cd delegation-service
   ./test-delegation-flow.sh
   ```

### Manual Testing Commands

After setup, you can test individual components:

```bash
# Test Polaris health
curl http://localhost:8181/q/health

# Test delegation service health  
curl http://localhost:8282/health

# Get delegation service token
curl -X POST http://localhost:8181/api/catalog/v1/oauth/tokens \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=delegation-service&client_secret=delegation-secret-2024&scope=PRINCIPAL_ROLE:ALL"

# Test table loading with delegation
curl -X GET "http://localhost:8181/api/catalog/v1/namespaces/test_ns/tables/test_table" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  -H "X-Iceberg-Access-Delegation: vended-credentials"
```

## Architecture

### New Architecture (Polaris API-based)
```
Polaris Server → Delegation Service → Polaris REST API → Storage
```

**Benefits:**
- **Security**: Delegation service doesn't need direct storage credentials
- **Consistency**: Uses the same table metadata loading path as Polaris
- **Authorization**: Proper permission checks through Polaris authentication
- **Abstraction**: Delegation service doesn't need storage-specific configuration

### Authentication Flow

1. **Polaris Authentication**: Delegation service authenticates with Polaris using OAuth client credentials
2. **Table Loading**: Uses Polaris's REST API to load table metadata with access delegation
3. **Storage Credentials**: Receives temporary storage credentials from Polaris
4. **File Operations**: Uses credentials to perform actual file deletion

## Configuration

### Delegation Service Principal

The delegation service needs its own Polaris principal with appropriate permissions:

```bash
# Create delegation service principal
curl -X POST "http://localhost:8181/api/management/v1/principal-roles" \
  -H "Authorization: Bearer $POLARIS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "delegation-service",
    "properties": {}
  }'

# Grant necessary permissions for table access
curl -X PUT "http://localhost:8181/api/management/v1/principal-roles/delegation-service/catalog-roles/catalog_name" \
  -H "Authorization: Bearer $POLARIS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "catalog_admin"
  }'
```

### System Properties

Configure the delegation service with these system properties:

```bash
# Polaris connection
-Dpolaris.base.url=http://localhost:8181

# Delegation service credentials
-Dpolaris.delegation.client.id=delegation-service
-Dpolaris.delegation.client.secret=delegation-secret
```

### Environment Variables

Alternatively, you can use environment variables:

```bash
export POLARIS_BASE_URL=http://localhost:8181
export POLARIS_DELEGATION_CLIENT_ID=delegation-service
export POLARIS_DELEGATION_CLIENT_SECRET=delegation-secret
```

## Usage

### Start Polaris with Delegation Enabled

```bash
./gradlew :polaris-runtime-service:quarkusRun \
  -Dpolaris.bootstrap.credentials=POLARIS,root,s3cr3t \
  -Dpolaris.delegation.enabled=true
```

### Start Delegation Service

```bash
./gradlew :delegation-service:run --args="8282"
```

### Test Table Cleanup

```bash
# Create a test table
curl -X POST "http://localhost:8181/api/catalog/v1/namespaces/test_ns/tables" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "test_table",
    "location": "s3://bucket/path/to/table",
    "schema": {"type": "struct", "fields": [{"id": 1, "name": "col1", "type": "string", "required": true}]}
  }'

# Drop table with purge (triggers delegation)
curl -X DELETE "http://localhost:8181/api/catalog/v1/namespaces/test_ns/tables/test_table?purgeRequested=true" \
  -H "Authorization: Bearer $TOKEN"
```

## Security Considerations

### Permission Model

The delegation service principal needs:
- **READ access** to table metadata
- **Storage delegation permissions** to receive temporary credentials
- **Access to all catalogs** where cleanup might be needed

### Credential Management

- **Short-lived tokens**: Uses OAuth tokens with limited lifetime
- **Scoped access**: Only gets credentials for specific table operations
- **No persistent storage**: Credentials are temporary and not stored

### Network Security

- **TLS**: Use HTTPS for production deployments
- **Network isolation**: Delegation service should only access Polaris
- **Firewall rules**: Restrict delegation service network access

## Troubleshooting

### Common Issues

1. **Authentication failures**:
   ```
   Failed to authenticate with Polaris: 401 - Unauthorized
   ```
   - Check client ID and secret configuration
   - Verify principal exists in Polaris

2. **Permission errors**:
   ```
   Failed to load table from Polaris: 403 - Forbidden
   ```
   - Ensure delegation service principal has proper grants
   - Check catalog-level permissions

3. **Connection errors**:
   ```
   java.net.ConnectException: Connection refused
   ```
   - Verify Polaris is running on configured URL
   - Check network connectivity

### Debug Logging

Enable debug logging to troubleshoot issues:

```bash
# Add to system properties
-Dlogback.configurationFile=logback-debug.xml

# Or set log level
-Dorg.apache.polaris.delegation.service.storage.StorageFileManager=DEBUG
```

## API Reference

### REST Endpoints Used

- **Token endpoint**: `POST /api/catalog/v1/oauth/tokens`
- **Load table**: `GET /api/catalog/v1/namespaces/{namespace}/tables/{table}`
- **Headers**: `X-Iceberg-Access-Delegation: vended-credentials`

### Response Format

```json
{
  "metadata": {
    "format-version": 2,
    "table-uuid": "uuid",
    "location": "s3://bucket/path",
    "snapshots": [...]
  },
  "config": {
    "s3.access-key-id": "temporary-key",
    "s3.secret-access-key": "temporary-secret",
    "s3.session-token": "temporary-token",
    "io-impl": "org.apache.iceberg.aws.s3.S3FileIO"
  }
}
```

## Development

### Building

```bash
./gradlew :delegation-service:build
```

### Testing

```bash
./gradlew :delegation-service:test
```

### Integration Testing

```bash
# Start both services
./gradlew :polaris-runtime-service:quarkusRun -Dpolaris.delegation.enabled=true &
./gradlew :delegation-service:run --args="8282" &

# Run integration tests
./gradlew :delegation-service:integrationTest
```
 