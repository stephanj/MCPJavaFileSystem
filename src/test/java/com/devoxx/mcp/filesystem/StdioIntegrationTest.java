package com.devoxx.mcp.filesystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.File;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test that verifies the MCP server works correctly in STDIO mode.
 * This test spawns the server as a subprocess and communicates via stdin/stdout.
 *
 * Run with: mvn test -Dgroups=integration (after mvn package -DskipTests)
 */
@Tag("integration")
class StdioIntegrationTest {

    private static final String JAR_PATH = "target/devoxx-filesystem-0.0.1-SNAPSHOT.jar";

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testStdioServerInitializationAndListTools() {
        // Verify the JAR exists
        File jarFile = new File(JAR_PATH);
        assertTrue(jarFile.exists(), "JAR file must exist. Run 'mvn package' first.");

        // Build server parameters for STDIO transport
        var serverParams = ServerParameters.builder("java")
                .args(
                    "-Dspring.ai.mcp.server.stdio=true",
                    "-Dspring.main.web-application-type=none",
                    "-Dspring.main.banner-mode=off",
                    "-Dlogging.pattern.console=",
                    "-jar",
                    jarFile.getAbsolutePath()
                )
                .build();

        // Create transport and client
        var transport = new StdioClientTransport(serverParams, new JacksonMcpJsonMapper(new ObjectMapper()));
        var client = McpClient.sync(transport).build();

        try {
            // Initialize the connection
            client.initialize();

            // List available tools
            ListToolsResult toolsResult = client.listTools();

            assertNotNull(toolsResult, "Tools result should not be null");
            assertNotNull(toolsResult.tools(), "Tools list should not be null");
            assertFalse(toolsResult.tools().isEmpty(), "Should have at least one tool");

            // Verify expected tools are present
            var toolNames = toolsResult.tools().stream()
                    .map(Tool::name)
                    .toList();

            System.out.println("Available tools: " + toolNames);

            assertTrue(toolNames.contains("readFile"), "Should have readFile tool");
            assertTrue(toolNames.contains("writeFile"), "Should have writeFile tool");
            assertTrue(toolNames.contains("listDirectory"), "Should have listDirectory tool");
            assertTrue(toolNames.contains("searchFiles"), "Should have searchFiles tool");
            assertTrue(toolNames.contains("editFile"), "Should have editFile tool");
            assertTrue(toolNames.contains("createDirectory"), "Should have createDirectory tool");
            assertTrue(toolNames.contains("grepFiles"), "Should have grepFiles tool");
            assertTrue(toolNames.contains("fetchWebpage"), "Should have fetchWebpage tool");
            assertTrue(toolNames.contains("executeBash"), "Should have executeBash tool");

            assertEquals(9, toolsResult.tools().size(), "Should have exactly 9 tools");

        } finally {
            // Clean up
            client.closeGracefully();
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testStdioServerToolExecution() {
        File jarFile = new File(JAR_PATH);
        assertTrue(jarFile.exists(), "JAR file must exist. Run 'mvn package' first.");

        var serverParams = ServerParameters.builder("java")
                .args(
                    "-Dspring.ai.mcp.server.stdio=true",
                    "-Dspring.main.web-application-type=none",
                    "-Dspring.main.banner-mode=off",
                    "-Dlogging.pattern.console=",
                    "-jar",
                    jarFile.getAbsolutePath()
                )
                .build();

        var transport = new StdioClientTransport(serverParams, new JacksonMcpJsonMapper(new ObjectMapper()));
        var client = McpClient.sync(transport).build();

        try {
            client.initialize();

            // Test readFile tool by reading the pom.xml (use absolute path)
            String pomPath = new File("pom.xml").getAbsolutePath();
            var request = new CallToolRequest("readFile", java.util.Map.of("fullPathFile", pomPath));
            var result = client.callTool(request);

            assertNotNull(result, "Tool result should not be null");
            assertNotNull(result.content(), "Content should not be null");
            assertFalse(result.content().isEmpty(), "Content should not be empty");

            // Print actual content for debugging
            System.out.println("Tool result isError: " + result.isError());
            System.out.println("Content type: " + result.content().get(0).getClass().getName());
            System.out.println("Content: " + result.content().get(0));

            assertFalse(result.isError(), "Tool should not return an error");

            // The content should contain the pom.xml content
            String content = result.content().get(0).toString();
            assertTrue(content.contains("devoxx") || content.contains("filesystem") || content.contains("spring"),
                    "Should contain project-related content, got: " + content.substring(0, Math.min(200, content.length())));

        } finally {
            client.closeGracefully();
        }
    }
}
