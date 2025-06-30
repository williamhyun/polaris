**Under Development** - This module is currently being developed as part of the Polaris Delegation Service.

# Polaris Delegation Service

An optional, independent service designed to handle long-running background operations offloaded from the main Polaris catalog service. This service enables the Polaris to maintain low-latency performance for metadata operations while allowing heavy background tasks to be managed and scaled independently.

## Overview

The Delegation Service is responsible for executing resource-intensive tasks that would otherwise impact the core performance of the Polaris Catalog. The initial implementation focuses on handling data file deletion processes for `DROP TABLE ... PURGE` commands.

## Key Features

- **Independent Scaling**: Can be scaled separately from the main Polaris Catalog workload
- **Resilient Task Execution**: Provides recovery capabilities and retry mechanisms
- **Secure Communication**: Establishes secure communication via mutual TLS with Polaris
- **One-to-One Model**: Each Polaris realm pairs with a dedicated Delegation Service instance

## Architecture

The Delegation Service operates as a standalone microservice that:

1. Receives task delegation requests from Polaris Catalog via REST API
2. Executes long-running operations
3. Maintains its own persistence layer for task tracking
4. Provides synchronous execution for the MVP with plans for async operations

## Initial Implementation

The MVP focuses on:
- **DROP TABLE WITH PURGE**: Synchronous delegation of data file deletion
- **Task Persistence**: Independent database schema for task state management

## Future Roadmap

- Asynchronous task submission and status tracking
- Integration with Polaris Asynchronous & Reliable Tasks Framework
- Additional delegated operations (compaction, snapshot garbage collection)
- Support for scheduled background tasks
