package com.devoxx.mcp.filesystem.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.PatternSyntaxException;

@Service
public class SearchFilesService {

    private final ObjectMapper mapper = new ObjectMapper();

    @Tool(description = """
       Recursively search for files and directories matching a pattern. Searches through all subdirectories from the
       starting path. The search is case-insensitive and matches partial names. Returns full paths to all matching
       items. Great for finding files when you don't know their exact location.""")
    public String searchFiles(@ToolParam(description = "The base path to search in") String path,
                              @ToolParam(description = "The pattern to search for") String pattern) {

        System.err.println("searchFiles: " + path);
        Map<String, Object> result = new HashMap<>();
        List<String> matchingPaths = new ArrayList<>();

        try {
            Path basePath = Paths.get(path);
            if (!Files.exists(basePath)) {
                result.put("success", false);
                result.put("error", "Path does not exist: " + path);
                return mapper.writeValueAsString(result);
            }

            // Create the PathMatcher with the provided pattern
            String normalizedPattern = pattern;
            // If it doesn't start with glob:, add it
            if (!normalizedPattern.startsWith("glob:")) {
                normalizedPattern = "glob:" + normalizedPattern;
            }

            final PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher(normalizedPattern);

            // Walk the file tree starting from the base path
            Files.walkFileTree(basePath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    Path relativePath = basePath.relativize(file);
                    if (pathMatcher.matches(relativePath) ||
                            pathMatcher.matches(file.getFileName())) {
                        matchingPaths.add(file.toString());
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    Path relativePath = basePath.relativize(dir);
                    if (pathMatcher.matches(relativePath) ||
                            pathMatcher.matches(dir.getFileName())) {
                        matchingPaths.add(dir.toString());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            // Return the matched paths as JSON
            result.put("success", true);
            result.put("matches", matchingPaths);
            result.put("count", matchingPaths.size());

            try {
                return mapper.writeValueAsString(result);
            } catch (Exception e) {
                return "{\"success\": false, \"error\": \"Failed to serialize result\"}";
            }

        } catch (PatternSyntaxException e) {
            result.put("success", false);
            result.put("error", "Invalid pattern syntax: " + e.getMessage());
            try {
                return mapper.writeValueAsString(result);
            } catch (Exception ex) {
                return "{\"success\": false, \"error\": \"Failed to serialize error result\"}";
            }
        } catch (IOException e) {
            result.put("success", false);
            result.put("error", "Failed to search the file system: " + e.getMessage());
            try {
                return mapper.writeValueAsString(result);
            } catch (Exception ex) {
                return "{\"success\": false, \"error\": \"Failed to serialize error result\"}";
            }
        }
    }
}
