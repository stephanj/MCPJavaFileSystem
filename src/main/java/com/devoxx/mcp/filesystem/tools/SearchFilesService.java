package com.devoxx.mcp.filesystem.tools;

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
public class SearchFilesService extends AbstractToolService {

    @Tool(description = """
       Recursively search for files and directories matching a pattern. Searches through all subdirectories from the
       starting path. The search is case-insensitive and matches partial names. Returns full paths to all matching
       items. Great for finding files when you don't know their exact location.""")
    public String searchFiles(@ToolParam(description = "The base path to search in") String path,
                              @ToolParam(description = "The pattern to search for") String pattern) {

        Map<String, Object> result = new HashMap<>();
        List<String> matchingPaths = new ArrayList<>();

        try {
            Path basePath = Paths.get(path);
            if (!Files.exists(basePath)) {
                result.put(SUCCESS, false);
                result.put(ERROR, "Path does not exist: " + path);
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
            Files.walkFileTree(basePath, new SimpleFileVisitor<>() {
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
                    // Exclude hidden directories (starting with .)
                    if (dir.getNameCount() > 0 &&
                        (dir.getName(dir.getNameCount() - 1).startsWith(".") ||
                         dir.getName(dir.getNameCount() - 1).startsWith("target"))) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    Path relativePath = basePath.relativize(dir);
                    if (pathMatcher.matches(relativePath) ||
                        pathMatcher.matches(dir.getFileName())) {
                        matchingPaths.add(dir.toString());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            // Return the matched paths as JSON

            result.put("matches", matchingPaths);
            result.put("count", matchingPaths.size());

            return successMessage(result);

        } catch (PatternSyntaxException e) {
            return errorMessage("Invalid pattern syntax: " + e.getMessage());

        } catch (IOException e) {
            return errorMessage("Failed to serialize error result");
        }
    }
}
