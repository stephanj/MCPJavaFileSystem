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

@Service
public class EditFileService {

    private final ObjectMapper mapper = new ObjectMapper();

    @Tool(description = """
    Make line-based edits to a text file. Each edit replaces exact line sequences with new content.
    Returns a git-style diff showing the changes made.
    """)
    public String editFile(
            @ToolParam(description = "The path to the file to edit") String path,
            @ToolParam(description = "List of edits to apply, each containing oldText and newText") String edits,
            @ToolParam(description = "If true, only show diff without changing the file", required = false) Boolean dryRun
    ) {
        Map<String, Object> result = new HashMap<>();

        // Default dryRun to false if not provided
        boolean isDryRun = dryRun != null && dryRun;

        try {
            Path filePath = Paths.get(path);

            if (!Files.exists(filePath)) {
                result.put("success", false);
                result.put("error", "File does not exist: " + path);
                return mapper.writeValueAsString(result);
            }

            if (!Files.isRegularFile(filePath)) {
                result.put("success", false);
                result.put("error", "Path is not a regular file: " + path);
                return mapper.writeValueAsString(result);
            }

            // Read the original file content
            String originalContent = Files.readString(filePath);
            String newContent = originalContent;

            // Debug the received edits parameter
            result.put("debug_received_edits", edits);

            // Parse the edits from JSON
            List<Map<String, String>> editsList;
            try {
                // Try to parse as a JSON array
                if (edits.startsWith("[")) {
                    editsList = mapper.readValue(edits, new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, String>>>() {});
                } else {
                    // If the input isn't a JSON array, try to create a single edit from it
                    Map<String, String> singleEdit = new HashMap<>();
                    try {
                        // Try to parse as a single JSON object
                        if (edits.startsWith("{")) {
                            singleEdit = mapper.readValue(edits, new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
                        } else {
                            // If we can't parse it as JSON, assume it's a simple oldText=newText format
                            result.put("debug_edits_parsing", "Attempting fallback parsing");
                            return mapper.writeValueAsString(result);
                        }
                    } catch (Exception ex) {
                        result.put("success", false);
                        result.put("error", "Failed to parse edits as either array or object: " + ex.getMessage());
                        return mapper.writeValueAsString(result);
                    }

                    if (!singleEdit.containsKey("oldText") || !singleEdit.containsKey("newText")) {
                        result.put("success", false);
                        result.put("error", "Edit must contain 'oldText' and 'newText' fields");
                        return mapper.writeValueAsString(result);
                    }

                    editsList = new ArrayList<>();
                    editsList.add(singleEdit);
                }
            } catch (Exception e) {
                result.put("success", false);
                result.put("error", "Failed to parse edits: " + e.getMessage());
                result.put("debug_edits_error", e.toString());
                return mapper.writeValueAsString(result);
            }

            // Apply each edit
            for (Map<String, String> edit : editsList) {
                String oldText = edit.get("oldText");
                String newText = edit.get("newText");

                if (oldText == null || newText == null) {
                    result.put("success", false);
                    result.put("error", "Each edit must contain 'oldText' and 'newText'");
                    return mapper.writeValueAsString(result);
                }

                // Replace the text
                if (!newContent.contains(oldText)) {
                    // Debug: Add file content info to help diagnose the issue
                    result.put("success", false);
                    result.put("error", "Could not find text to replace: " + oldText);

                    // Add helpful debugging information
                    if (originalContent.length() > 1000) {
                        result.put("filePreview", originalContent.substring(0, 1000) + "...");
                    } else {
                        result.put("filePreview", originalContent);
                    }

                    // Check for Maven tag issues - special handling for XML files
                    if (path.endsWith(".xml") || path.endsWith(".pom")) {
                        String nameTag = "<name>";
                        String nTag = "<n>";

                        // Check for the common issue with Maven POM files
                        if (oldText.contains(nameTag) && originalContent.contains(nTag)) {
                            String suggestion = "File uses <n> tags instead of <name> tags. Try replacing <name> with <n> in your search text.";
                            result.put("suggestion", suggestion);
                        }
                    }

                    // Check if there's something similar (might be whitespace issues)
                    if (oldText.length() > 30) {
                        String searchSample = oldText.substring(0, 30); // Use part of the search string
                        int idx = originalContent.indexOf(searchSample);
                        if (idx >= 0) {
                            // Found a partial match, extract context
                            int start = Math.max(0, idx - 20);
                            int end = Math.min(originalContent.length(), idx + searchSample.length() + 50);
                            result.put("partialMatch", "Found similar text: " + originalContent.substring(start, end));
                        }
                    }

                    return mapper.writeValueAsString(result);
                }

                newContent = newContent.replace(oldText, newText);
            }

            // Generate diff
            String diff = generateDiff(path, originalContent, newContent);

            // Write changes to file if not a dry run
            if (!isDryRun) {
                Files.writeString(filePath, newContent);
            }

            result.put("success", true);
            result.put("diff", diff);
            result.put("dryRun", isDryRun);
            result.put("editsApplied", editsList.size());

            return mapper.writeValueAsString(result);

        } catch (IOException e) {
            result.put("success", false);
            result.put("error", "Failed to edit file: " + e.getMessage());
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

    /**
     * Generate a git-style diff between original and modified content
     */
    private String generateDiff(String filePath, String originalContent, String modifiedContent) {
        // Simple line-based diff implementation
        StringBuilder diff = new StringBuilder();
        diff.append("--- " + filePath + "\t(original)\n");
        diff.append("+++ " + filePath + "\t(modified)\n");

        String[] originalLines = originalContent.split("\\n");
        String[] modifiedLines = modifiedContent.split("\\n");

        // Find differences using a simple approach
        // For a real implementation, consider using a diff library

        // This is a very simplified diff that just shows before/after
        // In a real implementation, you would use a proper diff algorithm
        if (!originalContent.equals(modifiedContent)) {
            diff.append("@@ -1," + originalLines.length + " +1," + modifiedLines.length + " @@\n");

            // Show removed lines (original content)
            for (String line : originalLines) {
                diff.append("-" + line + "\n");
            }

            // Show added lines (modified content)
            for (String line : modifiedLines) {
                diff.append("+" + line + "\n");
            }
        } else {
            diff.append("No changes\n");
        }

        return diff.toString();
    }
}
