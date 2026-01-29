package com.devoxx.mcp.filesystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;

/**
 * With stdio transport, the MCP server is automatically started by the client.
 * But you
 * have to build the server jar first:
 *
 * <pre>
 * ./mvnw clean install -DskipTests
 * </pre>
 */
public class ClientStdio {

	public static void main(String[] args) throws InterruptedException {

		var stdioParams = ServerParameters.builder("java")
				.args("-Dspring.ai.mcp.server.stdio=true", "-Dspring.main.web-application-type=none",
						"-Dlogging.pattern.console=", "-jar",
						"/Users/christiantzolov/Dev/projects/demo/MCPJavaFileSystem/target/devoxx-filesystem-0.0.1-SNAPSHOT.jar")
				.build();

		var transport = new StdioClientTransport(stdioParams, new JacksonMcpJsonMapper(new ObjectMapper()));
		var client = McpClient.sync(transport).build();

		client.initialize();

		// List and demonstrate tools
		ListToolsResult toolsList = client.listTools();
		System.out.println("Available Tools = " + toolsList);

		client.closeGracefully();
	}

}
