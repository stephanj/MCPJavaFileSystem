# Java Filesystem & Web MCP Server

This project implements a Model Context Protocol (MCP) server that provides filesystem operations and web access tools for Large Language Model (LLM) agents. It enables AI assistants to interact with both the local filesystem and web resources through a set of well-defined operations.

## Features

The server provides the following operations:

### Filesystem Operations
- **Reading Files**: Read the complete contents of a file with proper encoding detection
- **Writing Files**: Create or overwrite files with new content
- **Editing Files**: Make line-based edits with git-style diff generation
- **Searching Files**: Recursively search for files and directories using glob patterns
- **Listing Directories**: Get detailed listings of directory contents
- **Directory Creation**: Create directories and nested directory structures

### Web Operations
- **Web Page Fetching**: Retrieve content from web pages with configurable timeouts
- **HTML Content Extraction**: Extract text content from HTML documents

These operations are exposed as tools for Large Language Models using the Model Context Protocol (MCP), allowing AI systems to safely interact with the filesystem and access web resources.

## Example of the MCP Java tool with [DevoxxGenie](https://github.com/devoxx/DevoxxGenieIDEAPlugin)
<img width="987" alt="Screenshot 2025-03-26 at 09 52 26" src="https://github.com/user-attachments/assets/a46e0bc7-1efa-4dd7-b6fa-2d4437fbe310" />

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.6+
- Spring Boot 3.3.6
- Spring AI MCP Server components

### Building the Project

Build the project using Maven:

```bash
mvn clean package
```

### Running the Server

Run the server using the following command:

```bash
java -jar target/devoxx-filesystem-0.0.1-SNAPSHOT.jar
```

The server uses STDIO for communication, so it doesn't expose any HTTP endpoints. It's designed to be launched by an MCP client.

## Tool Services

### Filesystem Tools

#### ReadFileService

```java
readFile(String fullPathFile)
```
Reads the complete contents of a file from the file system. Handles various text encodings and provides detailed error messages if the file cannot be read.

#### WriteFileService

```java
writeFile(String path, String content)
```
Creates a new file or completely overwrites an existing file with new content. Creates parent directories if they don't exist.

#### EditFileService

```java
editFile(String path, String edits, Boolean dryRun)
```
Makes line-based edits to a text file. Each edit replaces exact line sequences with new content. Returns a git-style diff showing the changes made. The `dryRun` parameter allows viewing changes without applying them.

#### SearchFilesService

```java
searchFiles(String path, String pattern)
```
Recursively searches for files and directories matching a pattern. Searches through all subdirectories from the starting path. The search is case-insensitive and matches partial names.

#### ListDirectoryService

```java
listDirectory(String path)
```
Gets a detailed listing of all files and directories in a specified path. Results clearly distinguish between files and directories with additional metadata.

### Web Tools

#### FetchWebpageService

```java
fetchWebpage(String url, Integer timeoutMs)
```
Fetches or reads a webpage from a URL and returns its content. The service uses jsoup to connect to the webpage and retrieve its content. The optional `timeoutMs` parameter allows setting a custom connection timeout.

## Testing

### Running the Tests

A comprehensive set of unit tests is provided for all service classes. Run them using:

```bash
mvn test
```

The tests use JUnit 5 and Mockito for mocking external dependencies like the jsoup library for web requests.

### Test Client

A test client is provided in `ClientStdio.java` which demonstrates how to invoke the tools using the MCP protocol.

## Configuration

The application is configured via `application.properties`:

```properties
spring.main.web-application-type=none
spring.main.banner-mode=off
logging.pattern.console=

spring.ai.mcp.server.name=filesystem-server
spring.ai.mcp.server.version=0.0.1

logging.file.name=,/JavaFileSystemMCP/target/filesystem-server.log
```

## Project Structure

```
JavaFileSystemMCP/
  src/
    main/
      java/
        com/
          devoxx/
            mcp/
              filesystem/
                tools/
                  EditFileService.java
                  ReadFileService.java
                  WriteFileService.java
                  SearchFilesService.java
                  FetchWebpageService.java
                  ListDirectoryService.java
                McpServerApplication.java
      resources/
        application.properties
    test/
      java/
        com/
          devoxx/
            mcp/
              filesystem/
                tools/
                  ReadFileServiceTest.java
                  WriteFileServiceTest.java
                  EditFileServiceTest.java
                  SearchFilesServiceTest.java
                  FetchWebpageServiceTest.java
                  ListDirectoryServiceTest.java
                ClientStdio.java
  pom.xml
  README.md
```

## Dependencies

The project uses:
- Spring Boot 3.3.6
- Spring AI MCP Server Components
- Jackson for JSON processing
- jsoup for HTML parsing and web content retrieval
- JUnit 5 and Mockito for testing

## Implementation Notes

- The server is designed to operate using the STDIO transport mechanism
- Banner mode and console logging are disabled to allow the STDIO transport to work properly
- Error handling provides detailed information about issues encountered during operations
- Each tool service includes comprehensive error handling and returns results in a standardized JSON format
- The `EditFileService` includes sophisticated diff generation for tracking changes
- The `SearchFilesService` supports glob patterns for flexible file matching
- The `FetchWebpageService` includes configurable timeouts and robust error handling for web requests

## Integration with DevoxxGenie MCP Support

This server can be easily integrated with DevoxxGenie using the MCP (Model Context Protocol) support. Here's how to set it up:

### Configuration in DevoxxGenie

1. In DevoxxGenie, access the MCP Server configuration screen
2. Configure the server with the following settings:
   - **Name**: `JavaFilesystem` (or any descriptive name)
   - **Transport Type**: `STDIO`
   - **Command**: Full path to your Java executable (e.g., `/Library/Java/JavaVirtualMachines/liberica-jdk-23.jdk/Contents/Home/bin/java`)
   - **Arguments**:
     ```
     -Dspring.ai.mcp.server.stdio=true
     -Dspring.main.web-application-type=none
     -Dlogging.pattern.console=
     -jar
     /Users/stephan/IdeaProjects/JavaFileSystemMCP/target/devoxx-filesystem-0.0.1-SNAPSHOT.jar
     ```

     Enter each argument on a new line.

### Usage with DevoxxGenie

Once configured, DevoxxGenie will automatically discover the tools provided by this MCP server. The AI assistant can then use these tools to:

1. Read and write files on the local system
2. Search for files and directories
3. List directory contents
4. Make edits to existing files
5. Fetch web pages and extract content

All operations will be performed with the permissions of the user running the DevoxxGenie application.

### Using with Claude Desktop

Edit your claude_desktop_config.json file with the following:

```java
{
  "mcpServers": {
    "filesystem": {
      "command": "/Library/Java/JavaVirtualMachines/liberica-jdk-23.jdk/Contents/Home/bin/java",
      "args": [
        "-Dspring.ai.mcp.server.stdio=true",
        "-Dspring.main.web-application-type=none",
        "-Dlogging.pattern.console=",
        "-jar",
        "/Users/stephan/IdeaProjects/JavaFileSystemMCP/target/devoxx-filesystem-0.0.1-SNAPSHOT.jar"
      ]
    }  
  }
}
```

<img width="796" alt="image" src="https://github.com/user-attachments/assets/4af70010-2b9d-435f-90b7-38033961a2fb" />


## Security Considerations

When using this server, be aware that:
- The LLM agent will have access to read and write files on the host system
- The agent can fetch content from any accessible web URL
- Consider running the server with appropriate permissions and in a controlled environment
- The server does not implement authentication or authorization mechanisms
- Consider network firewall rules if restricting web access is required
