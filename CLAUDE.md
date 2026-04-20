# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Cobal** is a reactive load balancing library built natively on Kotlin coroutines. It provides abstractions for node discovery and load balancing with Flow-based event watching.

## Build & Development Commands

```bash
# Full build (compile, test, check, package)
./gradlew build

# Run all tests
./gradlew test

# Run tests for a specific module
./gradlew :core:test
./gradlew :langchain4j:test

# Run code quality checks (Detekt)
./gradlew detekt

# Run detekt with auto-correct
./gradlew detekt --auto-correct

# Verify code coverage (Kover merged report)
./gradlew koverMergedVerify

# Generate documentation (Dokka)
./gradlew :core:dokka

# Clean build artifacts
./gradlew clean

# Publish to local Maven repository
./gradlew publishToMavenLocal
```

## Module Architecture

- **`:core`** - Core load balancing abstractions (`Node`, `LoadBalancer` interfaces) built on `kotlinx.coroutines.flow`
- **`:langchain4j`** - LangChain4j integration module; adds AI/LLM capabilities
- **`:bom`** - Bill of Materials for centralized dependency management
- **`:code-coverage-report`** - Aggregated Kover coverage report across all modules

## Architecture Notes

- **Node abstraction** (`Node.kt`): Represents a load-balanced endpoint with `id`, `weight`, and optional `watch: Flow<NodeEvent>` for dynamic updates
- **LoadBalancer abstraction** (`LoadBalancer.kt`): Generic interface over `NODE : Node` with `choose()` selection
- The core module intentionally has minimal dependencies (only `kotlinx-coroutines-core`)
- LangChain4j module depends only on core + LangChain4j libraries

## Testing

- Uses JUnit Jupiter + Kotlin Test + MockK + fluent-assert
- Test retry plugin enabled: tests that fail in CI will retry up to 2 times
- Logback configuration at `config/logback.xml` is applied to test JVM via `-Dlogback.configurationFile`