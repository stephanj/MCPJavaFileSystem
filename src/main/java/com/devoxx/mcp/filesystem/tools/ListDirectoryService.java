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
public class ListDirectoryService extends AbstractToolService {

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
                result.put(SUCCESS, false);
                result.put(ERROR, "Path does not exist: " + path);
                return mapper.writeValueAsString(result);
            }

            if (!Files.isDirectory(dirPath)) {
                result.put(SUCCESS, false);
                result.put(ERROR, "Path is not a directory: " + path);
                return mapper.writeValueAsString(result);
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath)) {
                for (Path entry : stream) {

                    // Exclude hidden directories
                    if (entry.getFileName().toString().startsWith(".")) {
                        continue;
                    }

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

            sortResults(entries);

            result.put("path", path);
            result.put("entries", entries);
            result.put("count", entries.size());

            return successMessage(result);

        } catch (IOException e) {
            return errorMessage("Failed to list directory: " + e.getMessage());
        } catch (Exception e) {
            return errorMessage("Unexpected error: " + e.getMessage());
        }
    }

    private static void sortResults(List<Map<String, Object>> entries) {
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
    }
}
