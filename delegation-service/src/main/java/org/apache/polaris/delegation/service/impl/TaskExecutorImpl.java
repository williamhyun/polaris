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
package org.apache.polaris.delegation.service.impl;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.polaris.delegation.api.model.TaskExecutionRequest;
import org.apache.polaris.delegation.api.model.TaskExecutionResponse;
import org.apache.polaris.delegation.service.TaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Basic implementation of the TaskExecutor for API validation.
 *
 * <p>This minimal implementation validates the API contracts and provides a foundation for future
 * development. Tasks are acknowledged and return success responses.
 */
@ApplicationScoped
public class TaskExecutorImpl implements TaskExecutor {

  private static final Logger LOGGER = LoggerFactory.getLogger(TaskExecutorImpl.class);

  @Override
  public TaskExecutionResponse executeTask(TaskExecutionRequest request)
      throws TaskExecutionException {

    String operationType = request.getCommonPayload().getOperationType();
    LOGGER.info("REST API called - Task received: operation_type={}", operationType);

    // TODO: Actual task execution logic will be implemented here
    // For now, this is just demonstrating the REST server framework

    LOGGER.info("Returning placeholder response for REST API framework");

    // Return placeholder response to demonstrate API contract
    return new TaskExecutionResponse("COMPLETED", "Task executed successfully");
  }
}
