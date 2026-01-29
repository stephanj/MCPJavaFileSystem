# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Build the project (includes unit tests)
mvn clean package

# Run unit tests only (71 tests)
mvn test

# Run a single test class
mvn test -Dtest=EditFileServiceTest

# Run a specific test method
mvn test -Dtest=EditFileServiceTest#shouldSuccessfullyApplySingleEdit

# Run integration tests (requires pre-built JAR)
mvn package -DskipTests
mvn test -Pintegration-tests

# Run the server in SSE mode (HTTP on port 8081)
java -jar target/devoxx-filesystem-0.0.1-SNAPSHOT.jar

# Run the server in STDIO mode (for MCP clients)
java -Dspring.ai.mcp.server.stdio=true \
     -Dspring.main.web-application-type=none \
     -Dspring.main.banner-mode=off \
     -Dlogging.pattern.console= \
     -jar target/devoxx-filesystem-0.0.1-SNAPSHOT.jar
```

## Architecture Overview

This is a Spring Boot MCP (Model Context Protocol) server that exposes filesystem and web tools for LLM agents.

### Tool Registration Flow

1. **McpServerApplication.java** - Entry point with `@Bean mcpServices()` method
2. Services are injected via constructor and passed to `MethodToolCallbackProvider`
3. Spring AI auto-discovers methods annotated with `@Tool` in service classes

### Service Layer Pattern

All tool services follow this pattern:
- Extend `AbstractToolService` (provides JSON helpers: `successMessage()`, `errorMessage()`)
- Annotated with `@Service`
- Single public method with `@Tool` annotation
- Parameters use `@ToolParam` for LLM descriptions
- Return JSON strings with `{"success": true/false, ...}` structure

### Available Tools (9 total)

| Service | Tool Method | Purpose |
|---------|-------------|---------|
| ReadFileService | `readFile(fullPathFile)` | Read file contents |
| WriteFileService | `writeFile(path, content)` | Create/overwrite files |
| EditFileService | `editFile(path, edits, dryRun)` | Line-based edits with diff |
| ListDirectoryService | `listDirectory(path)` | List directory contents |
| SearchFilesService | `searchFiles(path, pattern)` | Glob pattern search |
| GrepFilesService | `grepFiles(directory, pattern, ...)` | Text search in files |
| CreateDirectoryService | `createDirectory(directories)` | Create directories |
| BashService | `executeBash(command, workingDirectory, timeout)` | Run shell commands |
| FetchWebpageService | `fetchWebpage(url, timeoutMs)` | Fetch web content |

### Transport Modes

- **SSE Mode** (default): HTTP server on port 8081, endpoint `/sse`
- **STDIO Mode**: For MCP clients like Claude Desktop or DevoxxGenie. **Critical**: requires `-Dspring.main.banner-mode=off` to prevent banner from corrupting JSON-RPC

### Test Structure

Tests use JUnit 5 with `@Nested` classes for organization:
```
ServiceTest
├── BasicFunctionalityTests
├── EdgeCasesTests
├── ErrorHandlingTests
└── [feature-specific groups]
```

Integration tests are tagged with `@Tag("integration")` and excluded by default (run with `-Pintegration-tests`).

### Key Files

- `AbstractToolService.java` - Base class with JSON response helpers
- `application.properties` - Server configuration (SSE mode by default)
- `mcp-servers-config.json` - Example MCP client configuration

### Security Constraints

BashService blocks destructive commands: `rm`, `rmdir`, `mv`, `del`, `erase`, `dd`, `mkfs`, `format`
