package com.devoxx.mcp.filesystem.tools;

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
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Service
public class EditFileService extends AbstractToolService {

    public static final String OLD_TEXT = "oldText";
    public static final String NEW_TEXT = "newText";

    @Tool(description = """
    Make line-based edits to a text file. Each edit replaces exact line sequences with new content.
    Returns a git-style diff showing the changes made.
    
    The edits parameter can be provided in three formats:
    1. JSON object: {"oldText": "text to replace", "newText": "replacement text"}
    2. JSON array: [{"oldText": "text1", "newText": "replacement1"}, {"oldText": "text2", "newText": "replacement2"}]
    3. Simple format: "oldText----newText" where ---- is the separator
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
                result.put(SUCCESS, false);
                result.put(ERROR, "File does not exist: " + path);
                return mapper.writeValueAsString(result);
            }

            if (!Files.isRegularFile(filePath)) {
                result.put(SUCCESS, false);
                result.put(ERROR, "Path is not a regular file: " + path);
                return mapper.writeValueAsString(result);
            }

            // Read the original file content
            String originalContent = Files.readString(filePath);
            String newContent = originalContent;

            // Debug the received edits parameter
            result.put("debug_received_edits", edits);

            // Parse the edits
            List<Map<String, String>> editsList = parseEdits(edits, result);
            if (editsList == null) {
                return mapper.writeValueAsString(result);
            }

            // Apply each edit
            List<String> appliedEdits = new ArrayList<>();
            for (Map<String, String> edit : editsList) {
                String oldText = edit.get(OLD_TEXT);
                String newText = edit.get(NEW_TEXT);
                boolean useRegex = Boolean.parseBoolean(edit.getOrDefault("useRegex", "false"));

                if (oldText == null || newText == null) {
                    result.put(SUCCESS, false);
                    result.put(ERROR, "Each edit must contain 'oldText' and 'newText'");
                    return mapper.writeValueAsString(result);
                }

                if (useRegex) {
                    try {
                        Pattern pattern = Pattern.compile(oldText, Pattern.DOTALL);
                        String tempContent = pattern.matcher(newContent).replaceFirst(newText);
                        
                        // Only count it as applied if a change was made
                        if (!tempContent.equals(newContent)) {
                            newContent = tempContent;
                            appliedEdits.add(oldText);
                        } else {
                            reportTextNotFound(oldText, originalContent, path, result);
                            return mapper.writeValueAsString(result);
                        }
                    } catch (PatternSyntaxException e) {
                        result.put(SUCCESS, false);
                        result.put(ERROR, "Invalid regex pattern: " + e.getMessage());
                        return mapper.writeValueAsString(result);
                    }
                } else {
                    // Simple text replacement (normalize line endings)
                    String normalizedContent = normalizeLineEndings(newContent);
                    String normalizedOldText = normalizeLineEndings(oldText);
                    
                    if (!normalizedContent.contains(normalizedOldText)) {
                        reportTextNotFound(oldText, originalContent, path, result);
                        return mapper.writeValueAsString(result);
                    }
                    
                    newContent = normalizedContent.replace(normalizedOldText, newText);
                    appliedEdits.add(oldText);
                }
            }

            // Generate diff
            String diff = generateDiff(path, originalContent, newContent);

            // Write changes to file if not a dry run
            if (!isDryRun && !originalContent.equals(newContent)) {
                Files.writeString(filePath, newContent);
            }

            result.put(SUCCESS, true);
            result.put("diff", diff);
            result.put("dryRun", isDryRun);
            result.put("editsApplied", appliedEdits.size());
            result.put("appliedEdits", appliedEdits);

            return mapper.writeValueAsString(result);

        } catch (IOException e) {
            return errorMessage("Failed to edit file: " + e.getMessage());
        } catch (Exception e) {
            return errorMessage("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Parse edits in various formats into a unified list.
     */
    private List<Map<String, String>> parseEdits(String edits, Map<String, Object> result) {
        List<Map<String, String>> editsList = new ArrayList<>();

        try {
            // Try to parse as a JSON array
            if (edits.startsWith("[")) {
                editsList = mapper.readValue(edits, new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, String>>>() {});
            } else if (edits.startsWith("{")) {
                // Parse as a single JSON object
                Map<String, String> singleEdit = mapper.readValue(edits, new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
                
                if (!singleEdit.containsKey(OLD_TEXT) || !singleEdit.containsKey(NEW_TEXT)) {
                    result.put(SUCCESS, false);
                    result.put(ERROR, "Edit must contain 'oldText' and 'newText' fields");
                    return null;
                }
                
                editsList.add(singleEdit);
            } else {
                // Try to parse as a simple format: "oldText----newText"
                result.put("debug_edits_parsing", "Attempting fallback parsing");
                
                String[] parts = edits.split("----", 2);
                if (parts.length == 2) {
                    Map<String, String> singleEdit = new HashMap<>();
                    singleEdit.put(OLD_TEXT, parts[0]);
                    singleEdit.put(NEW_TEXT, parts[1]);
                    editsList.add(singleEdit);
                } else {
                    result.put(SUCCESS, false);
                    result.put(ERROR, "Could not parse edits. Expected JSON format or 'oldText----newText' format.");
                    return List.of();
                }
            }
            
            return editsList;
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "Failed to parse edits: " + e.getMessage());
            result.put("debug_edits_error", e.toString());
            return List.of();
        }
    }

    /**
     * Report detailed information when text to replace is not found
     */
    private void reportTextNotFound(String oldText, String originalContent, String path, Map<String, Object> result) {
        result.put("success", false);
        result.put("error", "Could not find text to replace: " + (oldText.length() > 50 ? oldText.substring(0, 47) + "..." : oldText));

        // Add helpful debugging information
        if (originalContent.length() > 1000) {
            result.put("filePreview", originalContent.substring(0, 1000) + "...");
        } else {
            result.put("filePreview", originalContent);
        }

        // Check for whitespace issues
        result.put("oldTextLength", oldText.length());
        result.put("containsNewlines", oldText.contains("\n"));
        result.put("containsCarriageReturns", oldText.contains("\r"));
        result.put("containsSpaces", oldText.contains(" "));
        result.put("containsTabs", oldText.contains("\t"));

        // Special checks for XML/POM files
        if (path.endsWith(".xml") || path.endsWith(".pom")) {
            String nameTag = "<name>";
            String nTag = "<n>";

            // Check for common Maven POM issues
            if (oldText.contains(nameTag) && originalContent.contains(nTag)) {
                String suggestion = "File uses <n> tags instead of <name> tags. Try replacing <name> with <n> in your search text.";
                result.put("suggestion", suggestion);
            }
        }

        // Check for partial matches
        if (oldText.length() > 30) {
            String searchSample = oldText.substring(0, Math.min(30, oldText.length())); 
            int idx = originalContent.indexOf(searchSample);
            if (idx >= 0) {
                // Found a partial match, extract context
                int start = Math.max(0, idx - 20);
                int end = Math.min(originalContent.length(), idx + searchSample.length() + 50);
                result.put("partialMatch", "Found similar text: " + originalContent.substring(start, end));
            }
        }
    }

    /**
     * Normalize line endings to make replacements more consistent
     */
    private String normalizeLineEndings(String text) {
        // First convert all CRLF to LF
        String normalized = text.replace("\r\n", "\n");
        // Then convert any remaining CR to LF (for Mac old format)
        return normalized.replace("\r", "\n");
    }

    /**
     * Generate a git-style diff between original and modified content
     */
    private String generateDiff(String filePath, String originalContent, String modifiedContent) {
        // Normalize line endings
        originalContent = normalizeLineEndings(originalContent);
        modifiedContent = normalizeLineEndings(modifiedContent);
        
        // Simple line-based diff implementation
        StringBuilder diff = new StringBuilder();
        diff.append("--- ").append(filePath).append("\t(original)\n");
        diff.append("+++ ").append(filePath).append("\t(modified)\n");

        String[] originalLines = originalContent.split("\n");
        String[] modifiedLines = modifiedContent.split("\n");

        if (!originalContent.equals(modifiedContent)) {
            diff.append("@@ -1,").append(originalLines.length).append(" +1,").append(modifiedLines.length).append(" @@\n");

            // Show removed lines (original content)
            for (String line : originalLines) {
                diff.append("-").append(line).append("\n");
            }

            // Show added lines (modified content)
            for (String line : modifiedLines) {
                diff.append("+").append(line).append("\n");
            }
        } else {
            diff.append("No changes\n");
        }

        return diff.toString();
    }
}