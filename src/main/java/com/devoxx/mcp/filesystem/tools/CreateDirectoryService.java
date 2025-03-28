package com.devoxx.mcp.filesystem.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Service
public class CreateDirectoryService extends AbstractToolService {

    @Tool(description = """
                 Create a new directory or ensure a directory exists.
                 Can create multiple directories in one go.
                 If the directory already exists,this operation will succeed silently.
                 Perfect for setting up directory structures for projects or ensuring required paths exist.
            """)
    public String createDirectory(
            @ToolParam(description = "The list of directories to create") String[] directories
    ) {
        Map<String, Object> result = new HashMap<>();

        try {
            for (String directory : directories) {
                Path dirPath = Paths.get(directory);

                if (!Files.exists(dirPath)) {
                    try {
                        Files.createDirectories(dirPath);
                    } catch (IOException e) {
                        return errorMessage( "Failed to create directory: " + e.getMessage());
                    }
                }
            }
            return successMessage(result);
        } catch (Exception e) {
            return errorMessage("Unexpected error: " + e.getMessage());
        }
    }
}