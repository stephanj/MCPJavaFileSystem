package com.devoxx.mcp.filesystem.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
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
public class CreateDirectoryService {

    private final ObjectMapper mapper = new ObjectMapper();

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
                        result.put("success", false);
                        result.put("error", "Failed to create directory: " + e.getMessage());
                        return mapper.writeValueAsString(result);
                    }
                }
            }
            result.put("success", true);
            return mapper.writeValueAsString(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "Unexpected error: " + e.getMessage());
            try {
                return mapper.writeValueAsString(result);
            } catch (Exception ex) {
                return "{\"success\": false, \"error\": \"Failed to serialize error result\"}";
            }
        }
    }
}