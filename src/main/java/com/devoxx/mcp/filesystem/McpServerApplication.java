package com.devoxx.mcp.filesystem;

import com.devoxx.mcp.filesystem.tools.EditFileService;
import com.devoxx.mcp.filesystem.tools.ListDirectoryService;
import com.devoxx.mcp.filesystem.tools.ReadFileService;
import com.devoxx.mcp.filesystem.tools.SearchFilesService;
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
											ListDirectoryService listDirectoryService) {
		return MethodToolCallbackProvider.builder()
				.toolObjects(searchFilesService, listDirectoryService, editFileService, readFileService)
				.build();
	}
}
