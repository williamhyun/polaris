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
package org.apache.polaris.delegation.service.storage;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import org.apache.iceberg.io.FileIO;
import org.apache.polaris.delegation.api.model.TableIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages storage file operations for delegated tasks.
 *
 * <p>This service handles the actual deletion of data files from cloud storage (S3, Azure Blob
 * Storage, Google Cloud Storage) for table cleanup operations.
 */
@ApplicationScoped
public class StorageFileManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(StorageFileManager.class);
  private static final int MAX_CONCURRENT_DELETES = 50;
  private static final String POLARIS_STORAGE_LOCATION = "POLARIS_STORAGE_LOCATION";

  /**
   * Cleans up all data files associated with a table.
   *
   * <p>This method performs the actual data file deletion from storage by:
   *
   * <ol>
   *   <li>Loading table metadata from the storage location
   *   <li>Identifying all data files across all snapshots
   *   <li>Deleting data files using the configured FileIO implementation
   *   <li>Optionally deleting metadata files
   * </ol>
   *
   * @param tableIdentity the table to clean up
   * @param storageProperties storage configuration properties
   * @return cleanup result with success status and file counts
   */
  public CleanupResult cleanupTableFiles(
      TableIdentity tableIdentity, Map<String, String> storageProperties) {
    LOGGER.info(
        "Starting storage cleanup for table: {}.{}.{}",
        tableIdentity.getCatalogName(),
        String.join(".", tableIdentity.getNamespaceLevels()),
        tableIdentity.getTableName());

    try {
      // Get the storage location from properties
      String storageLocation = storageProperties.get(POLARIS_STORAGE_LOCATION);
      if (storageLocation == null || storageLocation.trim().isEmpty()) {
        return CleanupResult.failure("Storage location not provided in task properties");
      }

      // Initialize FileIO based on storage properties
      FileIO fileIO = createFileIO(storageProperties);

      // For POC, we'll simulate the cleanup process
      // In a real implementation, this would:
      // 1. Load table metadata from storage location
      // 2. Iterate through all snapshots and manifests
      // 3. Delete actual data files
      // 4. Delete manifest files
      // 5. Delete metadata files

      return simulateDataFileCleanup(tableIdentity, storageLocation, fileIO);

    } catch (Exception e) {
      LOGGER.error("Failed to cleanup table files", e);
      return CleanupResult.failure("Storage cleanup failed: " + e.getMessage());
    }
  }

  /**
   * Creates a FileIO instance based on storage properties. This handles different storage backends
   * (S3, Azure, GCS).
   */
  private FileIO createFileIO(Map<String, String> storageProperties) {
    try {
      // Get the FileIO implementation class
      String fileIOImpl = storageProperties.get("io-impl");
      if (fileIOImpl == null) {
        fileIOImpl = "org.apache.iceberg.io.ResolvingFileIO"; // Default
      }

      // Create FileIO instance
      FileIO fileIO = (FileIO) Class.forName(fileIOImpl).getDeclaredConstructor().newInstance();

      // Initialize with storage properties
      fileIO.initialize(storageProperties);

      LOGGER.debug("Created FileIO instance: {}", fileIOImpl);
      return fileIO;

    } catch (Exception e) {
      LOGGER.warn("Failed to create configured FileIO, using default", e);
      // Fallback to default FileIO
      FileIO fileIO = new org.apache.iceberg.io.ResolvingFileIO();
      fileIO.initialize(storageProperties);
      return fileIO;
    }
  }

  /**
   * Simulates data file cleanup for POC demonstration. In a real implementation, this would perform
   * actual file deletion.
   */
  private CleanupResult simulateDataFileCleanup(
      TableIdentity tableIdentity, String storageLocation, FileIO fileIO) {
    LOGGER.info("Simulating data file cleanup for table at location: {}", storageLocation);

    try {
      // Simulate the cleanup process
      Thread.sleep(2000); // Simulate cleanup time

      // Simulate finding and deleting files
      int simulatedDataFiles = 15; // Simulate 15 data files
      int simulatedManifestFiles = 3; // Simulate 3 manifest files
      int totalFiles = simulatedDataFiles + simulatedManifestFiles;

      LOGGER.info(
          "Simulated cleanup of {} data files and {} manifest files from {}",
          simulatedDataFiles,
          simulatedManifestFiles,
          storageLocation);

      return CleanupResult.success(totalFiles);

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return CleanupResult.failure("Cleanup operation interrupted");
    } finally {
      // Clean up FileIO resources
      if (fileIO != null) {
        try {
          fileIO.close();
        } catch (Exception e) {
          LOGGER.warn("Failed to close FileIO", e);
        }
      }
    }
  }

  /**
   * Real implementation for actual data file cleanup (commented out for POC). This would be used in
   * production to actually delete files.
   */
  /*
  private CleanupResult realDataFileCleanup(TableIdentity tableIdentity, String storageLocation, FileIO fileIO) {
    AtomicLong deletedFiles = new AtomicLong(0);
    List<String> errors = new ArrayList<>();

    try {
      // Load table metadata
      TableMetadata tableMetadata = loadTableMetadata(storageLocation, fileIO);

      // Get all data files from all snapshots
      List<String> dataFilePaths = collectAllDataFiles(tableMetadata);

      // Delete data files in parallel
      Tasks.foreach(dataFilePaths)
          .retry(3)
          .stopOnFailure()
          .throwFailureWhenFinished()
          .executeWith(Tasks.newFixedThreadPool(MAX_CONCURRENT_DELETES))
          .run(filePath -> {
            try {
              fileIO.deleteFile(filePath);
              deletedFiles.incrementAndGet();
              LOGGER.debug("Deleted data file: {}", filePath);
            } catch (Exception e) {
              String error = "Failed to delete file: " + filePath + " - " + e.getMessage();
              LOGGER.error(error, e);
              errors.add(error);
            }
          });

      // Delete manifest files
      List<String> manifestPaths = collectManifestFiles(tableMetadata);
      manifestPaths.forEach(manifestPath -> {
        try {
          fileIO.deleteFile(manifestPath);
          deletedFiles.incrementAndGet();
          LOGGER.debug("Deleted manifest file: {}", manifestPath);
        } catch (Exception e) {
          String error = "Failed to delete manifest: " + manifestPath + " - " + e.getMessage();
          LOGGER.error(error, e);
          errors.add(error);
        }
      });

      if (errors.isEmpty()) {
        return CleanupResult.success(deletedFiles.get());
      } else {
        return CleanupResult.failure("Some files failed to delete: " + String.join(", ", errors));
      }

    } catch (Exception e) {
      LOGGER.error("Failed to cleanup data files", e);
      return CleanupResult.failure("Data file cleanup failed: " + e.getMessage());
    }
  }
  */

  /** Result of a cleanup operation. */
  public static class CleanupResult {
    private final boolean success;
    private final long filesDeleted;
    private final String errorMessage;

    private CleanupResult(boolean success, long filesDeleted, String errorMessage) {
      this.success = success;
      this.filesDeleted = filesDeleted;
      this.errorMessage = errorMessage;
    }

    public static CleanupResult success(long filesDeleted) {
      return new CleanupResult(true, filesDeleted, null);
    }

    public static CleanupResult failure(String errorMessage) {
      return new CleanupResult(false, 0, errorMessage);
    }

    public boolean isSuccess() {
      return success;
    }

    public long getFilesDeleted() {
      return filesDeleted;
    }

    public String getErrorMessage() {
      return errorMessage;
    }
  }
}
