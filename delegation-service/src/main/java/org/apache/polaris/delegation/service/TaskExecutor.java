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
package org.apache.polaris.delegation.service;

import org.apache.polaris.delegation.api.model.TaskExecutionRequest;
import org.apache.polaris.delegation.api.model.TaskExecutionResponse;

/**
 * Service interface for executing delegated tasks within the Delegation Service.
 *
 * <p>This interface defines methods for executing tasks that have been delegated from the main
 * Polaris catalog service. It handles the actual execution of resource-intensive operations to
 * maintain low-latency performance for metadata operations in the catalog.
 */
public interface TaskExecutor {

  /**
   * Execute a delegated task.
   *
   * <p>The task will be executed synchronously and this method will block until the task completes
   * successfully or fails with an exception.
   *
   * @param request the task execution request containing task type, data, and metadata
   * @return the task execution response with task ID and completion information
   * @throws TaskExecutionException if the task execution fails
   */
  TaskExecutionResponse executeTask(TaskExecutionRequest request) throws TaskExecutionException;

  /** Exception thrown when task execution fails. */
  class TaskExecutionException extends Exception {
    public TaskExecutionException(String message) {
      super(message);
    }

    public TaskExecutionException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
