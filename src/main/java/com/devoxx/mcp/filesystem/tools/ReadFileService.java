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
public class ReadFileService {

    private final ObjectMapper mapper = new ObjectMapper();

    @Tool(description = """
    Read the complete contents of a file from the file system. Handles various text encodings and provides detailed
    error messages if the file cannot be read. Use this tool when you need to examine the contents of a single file.
    """)
    public String readFile(@ToolParam(description = "The full path to the file") String fullPathFile) {
        Map<String, Object> result = new HashMap<>();

        try {
            Path path = Paths.get(fullPathFile);

            if (!Files.exists(path)) {
                result.put("success", false);
                result.put("error", "File does not exist: " + fullPathFile);
                return mapper.writeValueAsString(result);
            }

            if (!Files.isRegularFile(path)) {
                result.put("success", false);
                result.put("error", "Path is not a regular file: " + fullPathFile);
                return mapper.writeValueAsString(result);
            }

            // Try to detect the file encoding (simplified here, uses default charset)
            String content = Files.readString(path);

            result.put("success", true);
            result.put("content", content);
            result.put("path", fullPathFile);
            result.put("size", Files.size(path));

            return mapper.writeValueAsString(result);

        } catch (IOException e) {
            result.put("success", false);
            result.put("error", "Failed to read file: " + e.getMessage());
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
