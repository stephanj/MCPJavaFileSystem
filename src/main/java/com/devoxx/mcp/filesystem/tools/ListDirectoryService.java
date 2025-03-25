package com.devoxx.mcp.filesystem.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ListDirectoryService {
    private final ObjectMapper mapper = new ObjectMapper();

    @Tool(description = """
    Get a detailed listing of all files and directories in a specified path. Results clearly distinguish between files and directories with 
    [FILE] and [DIR] prefixes. This tool is essential for understanding directory structure and finding specific files within a directory.
    """)
    public String listDirectory(@ToolParam(description = "The path to list contents of") String path) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> entries = new ArrayList<>();

        try {
            Path dirPath = Paths.get(path);

            if (!Files.exists(dirPath)) {
                result.put("success", false);
                result.put("error", "Path does not exist: " + path);
                return mapper.writeValueAsString(result);
            }

            if (!Files.isDirectory(dirPath)) {
                result.put("success", false);
                result.put("error", "Path is not a directory: " + path);
                return mapper.writeValueAsString(result);
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath)) {
                for (Path entry : stream) {
                    Map<String, Object> fileInfo = new HashMap<>();
                    boolean isDirectory = Files.isDirectory(entry);

                    fileInfo.put("name", entry.getFileName().toString());
                    fileInfo.put("type", isDirectory ? "DIR" : "FILE");
                    fileInfo.put("path", entry.toString());

                    // Add size for files
                    if (!isDirectory) {
                        try {
                            fileInfo.put("size", Files.size(entry));
                        } catch (IOException e) {
                            fileInfo.put("size", "unknown");
                        }
                    }

                    // Add last modified time
                    try {
                        fileInfo.put("lastModified", Files.getLastModifiedTime(entry).toMillis());
                    } catch (IOException e) {
                        fileInfo.put("lastModified", "unknown");
                    }

                    entries.add(fileInfo);
                }
            }

            // Sort entries: directories first, then files, both alphabetically
            entries.sort((a, b) -> {
                String typeA = (String) a.get("type");
                String typeB = (String) b.get("type");

                if (typeA.equals(typeB)) {
                    // Same type, sort by name
                    return ((String) a.get("name")).compareTo((String) b.get("name"));
                } else {
                    // Different types, directories come first
                    return typeA.equals("DIR") ? -1 : 1;
                }
            });

            result.put("success", true);
            result.put("path", path);
            result.put("entries", entries);
            result.put("count", entries.size());

            return mapper.writeValueAsString(result);

        } catch (IOException e) {
            result.put("success", false);
            result.put("error", "Failed to list directory: " + e.getMessage());
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
