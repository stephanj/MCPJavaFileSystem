package com.devoxx.mcp.filesystem.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

@Service
public class WriteFileService {

    private final ObjectMapper mapper = new ObjectMapper();

    @Tool(description = """
    Create a new file or completely overwrite an existing file with new content. Use with caution as it will overwrite existing files without warning.
    Handles text content with proper encoding.
    """)
    public String writeFile(
            @ToolParam(description = "The path to the file to create or overwrite") String path,
            @ToolParam(description = "The content to write to the file") String content
    ) {
        Map<String, Object> result = new HashMap<>();

        try {
            Path filePath = Paths.get(path);

            // Create parent directories if they don't exist
            Path parent = filePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
                result.put("createdDirectories", parent.toString());
            }

            // Write the content to the file
            boolean fileExisted = Files.exists(filePath);
            Files.writeString(filePath, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            result.put("success", true);
            result.put("path", path);
            result.put("bytesWritten", content.getBytes(StandardCharsets.UTF_8).length);
            result.put("action", fileExisted ? "overwritten" : "created");

            return mapper.writeValueAsString(result);

        } catch (IOException e) {
            result.put("success", false);
            result.put("error", "Failed to write file: " + e.getMessage());
            try {
                return mapper.writeValueAsString(result);
            } catch (Exception ex) {
                return "{\"success\": false, \"error\": \"Failed to serialize error result\"}";
            }
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