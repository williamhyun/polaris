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
package org.apache.polaris.service.task;

import org.apache.polaris.core.context.CallContext;
import org.apache.polaris.core.entity.AsyncTaskType;
import org.apache.polaris.core.entity.PolarisBaseEntity;
import org.apache.polaris.core.entity.TaskEntity;
import org.apache.polaris.core.entity.table.IcebergTableLikeEntity;
import org.apache.polaris.core.persistence.MetaStoreManagerFactory;
import org.apache.polaris.service.delegation.DelegationClient;
import org.apache.polaris.service.delegation.DelegationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TaskHandler that delegates table cleanup tasks to the delegation service when enabled.
 *
 * <p>This handler checks if delegation is configured and available, and if so, delegates table
 * cleanup tasks to the delegation service. If delegation fails or is not available, it falls back
 * to local execution using the TableCleanupTaskHandler.
 *
 * <p>This handler should be registered before the standard TableCleanupTaskHandler to intercept
 * delegatable tasks.
 */
public class DelegationTaskHandler implements TaskHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(DelegationTaskHandler.class);

  private final DelegationClient delegationClient;
  private final TaskHandler fallbackHandler;

  public DelegationTaskHandler(
      DelegationClient delegationClient,
      TaskExecutor taskExecutor,
      MetaStoreManagerFactory metaStoreManagerFactory,
      TaskFileIOSupplier fileIOSupplier) {
    this.delegationClient = delegationClient;
    // Create fallback handler for local execution
    this.fallbackHandler = new TableCleanupTaskHandler(taskExecutor, metaStoreManagerFactory, fileIOSupplier);
  }

  @Override
  public boolean canHandleTask(TaskEntity task) {
    // Handle table cleanup tasks that could potentially be delegated
    if (task.getTaskType() != AsyncTaskType.ENTITY_CLEANUP_SCHEDULER) {
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

  @Override
  public boolean handleTask(TaskEntity task, CallContext callContext) {
    // First, check if we can delegate this task
    if (delegationClient.canDelegate(task, callContext)) {
      try {
        LOGGER.info("Attempting to delegate task {} to delegation service", task.getId());
        boolean success = delegationClient.delegateTask(task, callContext);
        
        if (success) {
          LOGGER.info("Task {} successfully delegated to delegation service", task.getId());
          return true;
        } else {
          LOGGER.warn("Delegation service reported task {} as failed", task.getId());
        }
      } catch (DelegationException e) {
        LOGGER.error("Failed to delegate task {} to delegation service", task.getId(), e);
      }
    }

    // If delegation failed or is not available, fall back to local execution
    LOGGER.info("Falling back to local execution for task {}", task.getId());
    return fallbackHandler.handleTask(task, callContext);
  }
} 