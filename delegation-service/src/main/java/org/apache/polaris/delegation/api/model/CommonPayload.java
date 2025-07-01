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
package org.apache.polaris.delegation.api.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Common payload data included in all delegation tasks.
 *
 * <p>Contains global task information that applies to all task types within the delegation service.
 */
public class CommonPayload {

  @NotNull private final String operationType;

  @NotNull private final OffsetDateTime requestTimestampUtc;

  @NotNull private final String realmIdentifier;

  @JsonCreator
  public CommonPayload(
      @JsonProperty("operation_type") @NotNull String operationType,
      @JsonProperty("request_timestamp_utc") @NotNull OffsetDateTime requestTimestampUtc,
      @JsonProperty("realm_identifier") @NotNull String realmIdentifier) {
    this.operationType = operationType;
    this.requestTimestampUtc = requestTimestampUtc;
    this.realmIdentifier = realmIdentifier;
  }

  @JsonProperty("operation_type")
  public String getOperationType() {
    return operationType;
  }

  @JsonProperty("request_timestamp_utc")
  public OffsetDateTime getRequestTimestampUtc() {
    return requestTimestampUtc;
  }

  @JsonProperty("realm_identifier")
  public String getRealmIdentifier() {
    return realmIdentifier;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CommonPayload that = (CommonPayload) o;
    return Objects.equals(operationType, that.operationType)
        && Objects.equals(requestTimestampUtc, that.requestTimestampUtc)
        && Objects.equals(realmIdentifier, that.realmIdentifier);
  }

  @Override
  public int hashCode() {
    return Objects.hash(operationType, requestTimestampUtc, realmIdentifier);
  }

  @Override
  public String toString() {
    return "CommonPayload{"
        + "operationType='"
        + operationType
        + '\''
        + ", requestTimestampUtc="
        + requestTimestampUtc
        + ", realmIdentifier='"
        + realmIdentifier
        + '\''
        + '}';
  }
}
