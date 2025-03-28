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

    public static final int DEFAULT_MAX_RESULTS = 10;
    private final ObjectMapper mapper = new ObjectMapper();

    @Tool(description = """
            Search for text patterns within files. Returns matching files with line numbers and snippets.
            Similar to the Unix 'grep' command with optimized output for LLM processing.
            """)
    public String grepFiles(
            @ToolParam(description = "The base directory to search in") String directory,
            @ToolParam(description = "The pattern to search for in file contents") String pattern,
            @ToolParam(description = "Optional file extension filter (e.g., '.java', '.txt')", required = false) String fileExtension,
            @ToolParam(description = "Whether to use regex for pattern matching", required = false) Boolean useRegex,
            @ToolParam(description = "Number of context lines to include before/after matches", required = false) Integer contextLines,
            @ToolParam(description = "Maximum number of results to return", required = false) Integer maxResults
     ) {
        Map<String, Object> result = new HashMap<>();
        List<String> matchingSummaries = new ArrayList<>();
        int totalMatches = 0;
        int filesWithMatches = 0;
        
        // Default values
        boolean useRegexValue = useRegex != null && useRegex;
        int contextLinesValue = contextLines != null ? contextLines : 0;
        int maxResultsValue = maxResults != null ? maxResults : DEFAULT_MAX_RESULTS;

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
            Pattern searchPattern;
            int flags = Pattern.CASE_INSENSITIVE;
            if (useRegexValue) {
                searchPattern = Pattern.compile(pattern, flags);
            } else {
                String escapedPattern = Pattern.quote(pattern);
                searchPattern = Pattern.compile(escapedPattern, flags);
            }
            
            // Initialize gitignore utility if needed
            // GitignoreUtil gitignoreUtil = respectGitignoreValue ? new GitignoreUtil(directory) : null;
            
            // Find all files that match the extension filter and respect gitignore if enabled
            List<Path> filesToSearch = new ArrayList<>();
            try (Stream<Path> paths = Files.walk(basePath)) {
                paths.filter(Files::isRegularFile)
                     .filter(path -> fileExtension == null || fileExtension.isEmpty() || 
                                   path.toString().endsWith(fileExtension))
                     .forEach(filesToSearch::add);
            }
            
            // Search each file for the pattern
            for (Path filePath : filesToSearch) {
                if (totalMatches >= maxResultsValue) {
                    break;
                }
                
                try {
                    List<String> lines = Files.readAllLines(filePath);
                    List<String> fileMatches = new ArrayList<>();
                    
                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i);
                        Matcher matcher = searchPattern.matcher(line);
                        
                        if (matcher.find()) {
                            StringBuilder matchInfo = new StringBuilder();
                            matchInfo.append(filePath.getFileName()).append(":").append(i + 1).append(": ");
                            
                            // Add concise context if requested
                            if (contextLinesValue > 0) {
                                matchInfo.append("\n");
                                
                                // Context before
                                int startLine = Math.max(0, i - contextLinesValue);
                                if (startLine < i) {
                                    matchInfo.append("...\n");
                                }
                                
                                for (int j = startLine; j < i; j++) {
                                    matchInfo.append("  ").append(lines.get(j)).append("\n");
                                }
                                
                                // The matching line (highlighted)
                                matchInfo.append("→ ").append(line).append("\n");
                                
                                // Context after
                                int endLine = Math.min(lines.size(), i + 1 + contextLinesValue);
                                for (int j = i + 1; j < endLine; j++) {
                                    matchInfo.append("  ").append(lines.get(j)).append("\n");
                                }
                                
                                if (endLine < lines.size()) {
                                    matchInfo.append("...");
                                }
                            } else {
                                // Just the matching line without context
                                matchInfo.append(line);
                            }
                            
                            fileMatches.add(matchInfo.toString());
                            totalMatches++;
                            
                            if (totalMatches >= maxResultsValue) {
                                break;
                            }
                        }
                    }
                    
                    if (!fileMatches.isEmpty()) {
                        matchingSummaries.add(String.format("%s (%d matches)", 
                                               filePath.toString(), fileMatches.size()));
                        
                        // Limit the number of matches per file to keep response size manageable
                        int maxMatchesPerFile = Math.min(fileMatches.size(), 5);
                        for (int i = 0; i < maxMatchesPerFile; i++) {
                            matchingSummaries.add(fileMatches.get(i));
                        }
                        
                        if (fileMatches.size() > maxMatchesPerFile) {
                            matchingSummaries.add(String.format("... and %d more matches in this file", 
                                                 fileMatches.size() - maxMatchesPerFile));
                        }
                        
                        // Add a separator between files
                        matchingSummaries.add("---");
                        filesWithMatches++;
                    }
                } catch (IOException e) {
                    // Skip files that can't be read (binary files, etc.)
                }
            }
            
            // Remove the last separator if it exists
            if (!matchingSummaries.isEmpty() && matchingSummaries.get(matchingSummaries.size() - 1).equals("---")) {
                matchingSummaries.remove(matchingSummaries.size() - 1);
            }
            
            result.put("success", true);
            
            // Create a summary with gitignore information if applicable
            String summaryText = String.format("Found %d matches in %d files", totalMatches, filesWithMatches);

            result.put("summary", summaryText);
            result.put("results", matchingSummaries);
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