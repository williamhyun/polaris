/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.polaris.service.delegation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.iceberg.TableMetadata;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.polaris.core.context.CallContext;
import org.apache.polaris.core.entity.AsyncTaskType;
import org.apache.polaris.core.entity.table.IcebergTableLikeEntity;
import org.apache.polaris.core.entity.PolarisBaseEntity;
import org.apache.polaris.core.entity.TaskEntity;
import org.apache.polaris.service.config.DelegationServiceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of DelegationClient that communicates with the delegation service via HTTP.
 *
 * <p>This client converts Polaris internal task entities to delegation service API contracts and
 * handles HTTP communication with the delegation service.
 */
public class DelegationClientImpl implements DelegationClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(DelegationClientImpl.class);

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final DelegationServiceConfiguration config;

  public DelegationClientImpl(
      HttpClient httpClient,
      ObjectMapper objectMapper,
      DelegationServiceConfiguration config) {
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
    this.config = config;
  }

  @Override
  public boolean delegateTask(TaskEntity task, CallContext callContext) throws DelegationException {
    if (!canDelegate(task, callContext)) {
      return false;
    }

    try {
      // Convert TaskEntity to delegation service request
      TaskExecutionRequest request = convertToRequest(task, callContext);

      // Make HTTP call to delegation service
      String requestBody = objectMapper.writeValueAsString(request);
      HttpRequest httpRequest =
          HttpRequest.newBuilder()
              .uri(URI.create(config.getBaseUrl() + "/api/v1/tasks/execute/synchronous"))
              .header("Content-Type", "application/json")
              .header("Accept", "application/json")
              .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
              .POST(HttpRequest.BodyPublishers.ofString(requestBody))
              .build();

      LOGGER.info(
          "Delegating task {} to delegation service at {}",
          task.getId(),
          config.getBaseUrl());

      HttpResponse<String> response =
          httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        TaskExecutionResponse delegationResponse =
            objectMapper.readValue(response.body(), TaskExecutionResponse.class);
        LOGGER.info(
            "Task {} delegated successfully: status={}, summary={}",
            task.getId(),
            delegationResponse.getStatus(),
            delegationResponse.getResultSummary());
        return "COMPLETED".equals(delegationResponse.getStatus());
      } else {
        throw new DelegationException(
            String.format(
                "Delegation service returned error: %d %s", response.statusCode(), response.body()));
      }

    } catch (IOException | InterruptedException e) {
      throw new DelegationException("Failed to communicate with delegation service", e);
    }
  }

  @Override
  public boolean delegatePurge(
      TableIdentifier tableIdentifier,
      TableMetadata tableMetadata,
      Map<String, String> storageProperties,
      CallContext callContext) {
    
    if (!isDelegationEnabled()) {
      LOGGER.debug("Delegation service is not enabled for table {}", tableIdentifier);
      return false;
    }
    
    try {
      LOGGER.info("Sending synchronous request to delegation service for data file cleanup of table {}", tableIdentifier);
      
      // Send synchronous HTTP request to delegation service for data file cleanup
      // This call will BLOCK until delegation service completes the operation or fails
      boolean delegationSuccessful = delegateTablePurge(
          tableIdentifier, 
          storageProperties, 
          callContext);
      
      if (delegationSuccessful) {
        LOGGER.info("Delegation service SYNCHRONOUSLY COMPLETED data file cleanup for table {}", tableIdentifier);
        return true;
      } else {
        LOGGER.error("Delegation service failed to complete data file cleanup for table {}", tableIdentifier);
        return false;
      }
      
    } catch (Exception e) {
      LOGGER.error("Error during delegation for table {}", tableIdentifier, e);
      return false;
    }
  }

  /**
   * Delegates the actual purge operation to the delegation service.
   */
  private boolean delegateTablePurge(
      TableIdentifier tableIdentifier,
      Map<String, String> storageProperties,
      CallContext callContext) throws DelegationException {
    
    try {
      String[] namespaceLevels = tableIdentifier.namespace().levels();
      List<String> namespaceLevelsList = Arrays.asList(namespaceLevels);
      
      // Create common payload
      CommonPayload commonPayload =
          new CommonPayload(
              TaskType.PURGE_TABLE,
              OffsetDateTime.now(),
              callContext.getRealmContext().getRealmIdentifier());

      // Create table identity
      TableIdentity tableIdentity = new TableIdentity(
          namespaceLevelsList.get(0), // catalog name
          namespaceLevelsList.subList(1, namespaceLevelsList.size()), // namespace levels
          tableIdentifier.name()); // table name

      // Create operation parameters
      TablePurgeParameters operationParameters = new TablePurgeParameters(tableIdentity, storageProperties);

      // Create request
      TaskExecutionRequest request = new TaskExecutionRequest(commonPayload, operationParameters);

      // Make HTTP call to delegation service
      String requestBody = objectMapper.writeValueAsString(request);
      HttpRequest httpRequest =
          HttpRequest.newBuilder()
              .uri(URI.create(config.getBaseUrl() + "/api/v1/tasks/execute/synchronous"))
              .header("Content-Type", "application/json")
              .header("Accept", "application/json")
              .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
              .POST(HttpRequest.BodyPublishers.ofString(requestBody))
              .build();

      LOGGER.info(
          "Sending BLOCKING HTTP request to delegation service at {} for table {}",
          config.getBaseUrl(),
          tableIdentifier);

      // CRITICAL: This is a SYNCHRONOUS/BLOCKING call that waits for delegation service to complete
      // The HTTP client will block this thread until:
      // 1. Delegation service completes data file cleanup and responds, OR  
      // 2. Request times out (configured timeout: {} seconds), OR
      // 3. Network/service error occurs
      LOGGER.debug("Waiting for delegation service to complete data file cleanup (timeout: {}s)...", config.getTimeoutSeconds());
      
      HttpResponse<String> response =
          httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        TaskExecutionResponse delegationResponse =
            objectMapper.readValue(response.body(), TaskExecutionResponse.class);
        LOGGER.info(
            "Delegation service responded: status={}, summary={}",
            delegationResponse.getStatus(),
            delegationResponse.getResultSummary());
        
        boolean success = "success".equals(delegationResponse.getStatus());
        if (success) {
          LOGGER.info("Delegation service completed data file cleanup for table {}", tableIdentifier);
        } else {
          LOGGER.error("Delegation service reported failure for table {}: {}", tableIdentifier, delegationResponse.getResultSummary());
        }
        return success;
      } else {
        LOGGER.error(
            "Delegation service returned HTTP error: {} {} for table {}", 
            response.statusCode(), response.body(), tableIdentifier);
        return false;
      }

    } catch (IOException | InterruptedException e) {
      throw new DelegationException("Failed to communicate with delegation service", e);
    }
  }

  /**
   * Checks if delegation service is enabled.
   */
  private boolean isDelegationEnabled() {
    String delegationEnabled = System.getProperty("polaris.delegation.enabled", 
        System.getenv().getOrDefault("POLARIS_DELEGATION_ENABLED", "false"));
    return "true".equalsIgnoreCase(delegationEnabled) && config.isEnabled();
  }

  @Override
  public boolean canDelegate(TaskEntity task, CallContext callContext) {
    if (!config.isEnabled()) {
      return false;
    }

    // Only delegate table cleanup tasks for now
    AsyncTaskType taskType = task.getTaskType();
    if (taskType != AsyncTaskType.ENTITY_CLEANUP_SCHEDULER) {
      return false;
    }

    // Check if this is a table cleanup task
    try {
      PolarisBaseEntity entity = task.readData(PolarisBaseEntity.class);
      return IcebergTableLikeEntity.of(entity) != null;
    } catch (Exception e) {
      LOGGER.warn("Failed to read task data for delegation check", e);
      return false;
    }
  }

  private TaskExecutionRequest convertToRequest(TaskEntity task, CallContext callContext)
      throws DelegationException {
    try {
      // Read the table entity from the task
      PolarisBaseEntity entity = task.readData(PolarisBaseEntity.class);
      IcebergTableLikeEntity tableEntity = IcebergTableLikeEntity.of(entity);
      if (tableEntity == null) {
        throw new DelegationException("Task does not contain table entity data");
      }

      // Create common payload
      CommonPayload commonPayload =
          new CommonPayload(
              TaskType.PURGE_TABLE,
              OffsetDateTime.now(),
              callContext.getRealmContext().getRealmIdentifier());

      // Create table identity
      TableIdentifier tableId = tableEntity.getTableIdentifier();
      TableIdentity tableIdentity =
          new TableIdentity(
              tableId.namespace().toString(), // This may need adjustment for multi-level namespaces
              java.util.List.of(tableId.namespace().levels()),
              tableId.name());

      // Get properties from task's internal properties
      Map<String, String> properties = task.getInternalPropertiesAsMap();

      // Create operation parameters
      TablePurgeParameters operationParameters = new TablePurgeParameters(tableIdentity, properties);

      return new TaskExecutionRequest(commonPayload, operationParameters);

    } catch (Exception e) {
      throw new DelegationException("Failed to convert task to delegation request", e);
    }
  }

  // Inner classes for API contracts (these would normally be imported from delegation service)
  // For now, define them here to avoid dependency issues

  public static class TaskExecutionRequest {
    private final CommonPayload commonPayload;
    private final TablePurgeParameters operationParameters;

    public TaskExecutionRequest(CommonPayload commonPayload, TablePurgeParameters operationParameters) {
      this.commonPayload = commonPayload;
      this.operationParameters = operationParameters;
    }

    public CommonPayload getCommonPayload() { return commonPayload; }
    public TablePurgeParameters getOperationParameters() { return operationParameters; }
  }

  public static class CommonPayload {
    private final TaskType taskType;
    private final OffsetDateTime requestTimestampUtc;
    private final String realmIdentifier;

    public CommonPayload(TaskType taskType, OffsetDateTime requestTimestampUtc, String realmIdentifier) {
      this.taskType = taskType;
      this.requestTimestampUtc = requestTimestampUtc;
      this.realmIdentifier = realmIdentifier;
    }

    public TaskType getTaskType() { return taskType; }
    public OffsetDateTime getRequestTimestampUtc() { return requestTimestampUtc; }
    public String getRealmIdentifier() { return realmIdentifier; }
  }

  public static class TablePurgeParameters {
    private final TableIdentity tableIdentity;
    private final Map<String, String> properties;

    public TablePurgeParameters(TableIdentity tableIdentity, Map<String, String> properties) {
      this.tableIdentity = tableIdentity;
      this.properties = properties;
    }

    public TableIdentity getTableIdentity() { return tableIdentity; }
    public Map<String, String> getProperties() { return properties; }
  }

  public static class TableIdentity {
    private final String catalogName;
    private final java.util.List<String> namespaceLevels;
    private final String tableName;

    public TableIdentity(String catalogName, java.util.List<String> namespaceLevels, String tableName) {
      this.catalogName = catalogName;
      this.namespaceLevels = namespaceLevels;
      this.tableName = tableName;
    }

    public String getCatalogName() { return catalogName; }
    public java.util.List<String> getNamespaceLevels() { return namespaceLevels; }
    public String getTableName() { return tableName; }
  }

  public static class TaskExecutionResponse {
    private final String status;
    private final String resultSummary;

    @JsonCreator
    public TaskExecutionResponse(@JsonProperty("status") String status, @JsonProperty("result_summary") String resultSummary) {
      this.status = status;
      this.resultSummary = resultSummary;
    }

    public String getStatus() { return status; }
    public String getResultSummary() { return resultSummary; }
  }

  public enum TaskType {
    PURGE_TABLE
  }
} 