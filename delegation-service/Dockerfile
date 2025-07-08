# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

FROM eclipse-temurin:21-jre-alpine

LABEL org.opencontainers.image.source="https://github.com/apache/polaris"
LABEL org.opencontainers.image.description="Polaris Delegation Service"
LABEL org.opencontainers.image.licenses="Apache-2.0"

# Create a user for running the service
RUN addgroup -S polaris && adduser -S polaris -G polaris

# Set working directory
WORKDIR /app

# Copy the built JAR file (this will be created by the build process)
COPY build/libs/delegation-service-*.jar app.jar

# Set ownership
RUN chown -R polaris:polaris /app
USER polaris

# Environment variables with defaults
ENV POLARIS_DELEGATION_HOST=0.0.0.0
ENV POLARIS_DELEGATION_PORT=8080
ENV JAVA_OPTS="-Xms512m -Xmx2g"

# Expose the port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

# Run the service
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"] 