package com.devoxx.mcp.filesystem.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class GrepFilesService {

    private final ObjectMapper mapper = new ObjectMapper();

    @Tool(description = """
            Search for text patterns within files. Returns matching files with line numbers and context.
            Similar to the Unix 'grep' command but with additional features for context display.
            """)
    public String grepFiles(
            @ToolParam(description = "The base directory to search in") String directory,
            @ToolParam(description = "The pattern to search for in file contents") String pattern,
            @ToolParam(description = "Optional file extension filter (e.g., '.java', '.txt')", required = false) String fileExtension,
            @ToolParam(description = "Whether to use regex for pattern matching", required = false) Boolean useRegex,
            @ToolParam(description = "Number of context lines to include before/after matches", required = false) Integer contextLines,
            @ToolParam(description = "Maximum number of results to return", required = false) Integer maxResults,
            @ToolParam(description = "Whether to ignore case in pattern matching", required = false) Boolean ignoreCase
    ) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> matches = new ArrayList<>();
        int totalMatches = 0;
        int totalFiles = 0;
        
        // Default values
        boolean useRegexValue = useRegex != null && useRegex;
        int contextLinesValue = contextLines != null ? contextLines : 0;
        int maxResultsValue = maxResults != null ? maxResults : 100;
        boolean ignoreCaseValue = ignoreCase != null && ignoreCase;
        
        try {
            Path basePath = Paths.get(directory);
            
            if (!Files.exists(basePath)) {
                result.put("success", false);
                result.put("error", "Directory does not exist: " + directory);
                return mapper.writeValueAsString(result);
            }
            
            if (!Files.isDirectory(basePath)) {
                result.put("success", false);
                result.put("error", "Path is not a directory: " + directory);
                return mapper.writeValueAsString(result);
            }
            
            // Prepare pattern for matching
            Pattern searchPattern = null;
            if (useRegexValue) {
                int flags = ignoreCaseValue ? Pattern.CASE_INSENSITIVE : 0;
                searchPattern = Pattern.compile(pattern, flags);
            } else {
                String escapedPattern = Pattern.quote(pattern);
                int flags = ignoreCaseValue ? Pattern.CASE_INSENSITIVE : 0;
                searchPattern = Pattern.compile(escapedPattern, flags);
            }
            
            // Keep track of files searched and matches found
            List<Path> filesToSearch = new ArrayList<>();
            
            // Find all files that match the extension filter
            try (Stream<Path> paths = Files.walk(basePath)) {
                paths.filter(Files::isRegularFile)
                     .filter(path -> fileExtension == null || fileExtension.isEmpty() || 
                                   path.toString().endsWith(fileExtension))
                     .forEach(filesToSearch::add);
            }
            
            totalFiles = filesToSearch.size();
            
            // Search each file for the pattern
            for (Path filePath : filesToSearch) {
                if (totalMatches >= maxResultsValue) {
                    break;
                }
                
                try {
                    List<String> lines = Files.readAllLines(filePath);
                    List<Map<String, Object>> fileMatches = new ArrayList<>();
                    boolean fileHasMatches = false;
                    
                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i);
                        Matcher matcher = searchPattern.matcher(line);
                        
                        if (matcher.find()) {
                            Map<String, Object> match = new HashMap<>();
                            match.put("lineNumber", i + 1);
                            match.put("line", line);
                            
                            // Add context lines if requested
                            if (contextLinesValue > 0) {
                                List<String> contextBefore = new ArrayList<>();
                                List<String> contextAfter = new ArrayList<>();
                                
                                // Get context before
                                for (int j = Math.max(0, i - contextLinesValue); j < i; j++) {
                                    contextBefore.add(lines.get(j));
                                }
                                
                                // Get context after
                                for (int j = i + 1; j < Math.min(lines.size(), i + 1 + contextLinesValue); j++) {
                                    contextAfter.add(lines.get(j));
                                }
                                
                                match.put("contextBefore", contextBefore);
                                match.put("contextAfter", contextAfter);
                            }
                            
                            fileMatches.add(match);
                            fileHasMatches = true;
                            totalMatches++;
                            
                            if (totalMatches >= maxResultsValue) {
                                break;
                            }
                        }
                    }
                    
                    if (fileHasMatches) {
                        Map<String, Object> fileResult = new HashMap<>();
                        fileResult.put("file", filePath.toString());
                        fileResult.put("matches", fileMatches);
                        matches.add(fileResult);
                    }
                } catch (IOException e) {
                    // Skip files that can't be read (binary files, etc.)
                    continue;
                }
            }
            
            result.put("success", true);
            result.put("matches", matches);
            result.put("totalMatches", totalMatches);
            result.put("totalFiles", totalFiles);
            result.put("limitReached", totalMatches >= maxResultsValue);
            
            return mapper.writeValueAsString(result);
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "Error searching files: " + e.getMessage());
            try {
                return mapper.writeValueAsString(result);
            } catch (Exception ex) {
                return "{\"success\": false, \"error\": \"Failed to serialize error result\"}";
            }
        }
    }
}