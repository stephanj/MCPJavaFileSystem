package com.devoxx.mcp.filesystem;

import com.devoxx.mcp.filesystem.tools.*;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class McpServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(McpServerApplication.class, args);
	}

	@Bean
	public ToolCallbackProvider mcpServices(SearchFilesService searchFilesService,
											ReadFileService readFileService,
											EditFileService editFileService,
											ListDirectoryService listDirectoryService,
											WriteFileService writeFileService,
											FetchWebpageService fetchWebpageService) {
		return MethodToolCallbackProvider.builder()
				.toolObjects(searchFilesService,
						listDirectoryService,
						editFileService,
						readFileService,
						writeFileService,
						fetchWebpageService)
				.build();
	}
}