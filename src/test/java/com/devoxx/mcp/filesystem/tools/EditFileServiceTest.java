package com.devoxx.mcp.filesystem.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EditFileServiceTest {

    private EditFileService editFileService;
    private ObjectMapper objectMapper;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        editFileService = new EditFileService();
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldSuccessfullyApplySingleEdit() throws Exception {
        // Given
        String initialContent = "Line 1\nLine 2\nLine 3\nLine 4";
        Path testFile = tempDir.resolve("test-edit.txt");
        Files.writeString(testFile, initialContent);
        
        String edits = "[{\"oldText\":\"Line 2\",\"newText\":\"Modified Line 2\"}]";

        // When
        String result = editFileService.editFile(testFile.toString(), edits, false);
        JsonNode jsonResult = objectMapper.readTree(result);

        // Then
        assertTrue(jsonResult.get("success").asBoolean());
        assertEquals(1, jsonResult.get("editsApplied").asInt());
        assertFalse(jsonResult.get("dryRun").asBoolean());
        
        // Verify file was actually modified
        String expectedContent = "Line 1\nModified Line 2\nLine 3\nLine 4";
        assertEquals(expectedContent, Files.readString(testFile));
    }

    @Test
    void shouldSuccessfullyApplyMultipleEdits() throws Exception {
        // Given
        String initialContent = "Line 1\nLine 2\nLine 3\nLine 4";
        Path testFile = tempDir.resolve("test-multiple-edits.txt");
        Files.writeString(testFile, initialContent);
        
        String edits = "[" +
                "{\"oldText\":\"Line 1\",\"newText\":\"Modified Line 1\"}," +
                "{\"oldText\":\"Line 3\",\"newText\":\"Modified Line 3\"}" +
                "]";

        // When
        String result = editFileService.editFile(testFile.toString(), edits, false);
        JsonNode jsonResult = objectMapper.readTree(result);

        // Then
        assertTrue(jsonResult.get("success").asBoolean());
        assertEquals(2, jsonResult.get("editsApplied").asInt());
        
        // Verify file was actually modified with both edits
        String expectedContent = "Modified Line 1\nLine 2\nModified Line 3\nLine 4";
        assertEquals(expectedContent, Files.readString(testFile));
    }

    @Test
    void shouldHandleDryRunMode() throws Exception {
        // Given
        String initialContent = "Line 1\nLine 2\nLine 3";
        Path testFile = tempDir.resolve("test-dry-run.txt");
        Files.writeString(testFile, initialContent);
        
        String edits = "[{\"oldText\":\"Line 2\",\"newText\":\"Should Not Change\"}]";

        // When
        String result = editFileService.editFile(testFile.toString(), edits, true);
        JsonNode jsonResult = objectMapper.readTree(result);

        // Then
        assertTrue(jsonResult.get("success").asBoolean());
        assertTrue(jsonResult.get("dryRun").asBoolean());
        assertTrue(jsonResult.has("diff"));
        
        // Verify file was NOT modified since this was a dry run
        assertEquals(initialContent, Files.readString(testFile));
    }

    @Test
    void shouldHandleNonExistentFile() throws Exception {
        // Given
        Path nonExistentFile = tempDir.resolve("non-existent.txt");
        String edits = "[{\"oldText\":\"Any Text\",\"newText\":\"New Text\"}]";

        // When
        String result = editFileService.editFile(nonExistentFile.toString(), edits, false);
        JsonNode jsonResult = objectMapper.readTree(result);

        // Then
        assertFalse(jsonResult.get("success").asBoolean());
        assertTrue(jsonResult.get("error").asText().contains("File does not exist"));
    }

    @Test
    void shouldHandleNonMatchingText() throws Exception {
        // Given
        String initialContent = "Line 1\nLine 2\nLine 3";
        Path testFile = tempDir.resolve("non-matching.txt");
        Files.writeString(testFile, initialContent);
        
        String edits = "[{\"oldText\":\"This text does not exist\",\"newText\":\"Replacement\"}]";

        // When
        String result = editFileService.editFile(testFile.toString(), edits, false);
        JsonNode jsonResult = objectMapper.readTree(result);

        // Then
        assertFalse(jsonResult.get("success").asBoolean());
        assertTrue(jsonResult.get("error").asText().contains("Could not find text to replace"));
        
        // Verify file was not modified
        assertEquals(initialContent, Files.readString(testFile));
    }

    @Test
    void shouldHandleMultilineTextReplacement() throws Exception {
        // Given
        String initialContent = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5";
        Path testFile = tempDir.resolve("multiline.txt");
        Files.writeString(testFile, initialContent);
        
        String edits = "[{\"oldText\":\"Line 2\\nLine 3\\nLine 4\",\"newText\":\"Replaced multiple lines\"}]";

        // When
        String result = editFileService.editFile(testFile.toString(), edits, false);
        JsonNode jsonResult = objectMapper.readTree(result);

        // Then
        assertTrue(jsonResult.get("success").asBoolean());
        
        // Verify multiline replacement worked
        String expectedContent = "Line 1\nReplaced multiple lines\nLine 5";
        assertEquals(expectedContent, Files.readString(testFile));
    }

    @Test
    void shouldHandleMalformedEditsJson() throws Exception {
        // Given
        String initialContent = "Test content";
        Path testFile = tempDir.resolve("malformed-json.txt");
        Files.writeString(testFile, initialContent);
        
        String edits = "This is not valid JSON";

        // When
        String result = editFileService.editFile(testFile.toString(), edits, false);
        JsonNode jsonResult = objectMapper.readTree(result);

        // Then
        assertTrue(jsonResult.get("debug_received_edits").asText().equals("This is not valid JSON"));
        assertTrue(jsonResult.get("debug_edits_parsing").asText().equals("Attempting fallback parsing"));
        
        // Verify file was not modified
        assertEquals(initialContent, Files.readString(testFile));
    }

    @Test
    void shouldHandleIncompleteEditsObject() throws Exception {
        // Given
        String initialContent = "Test content";
        Path testFile = tempDir.resolve("incomplete-json.txt");
        Files.writeString(testFile, initialContent);
        
        // Missing newText field
        String edits = "[{\"oldText\":\"Test content\"}]";

        // When
        String result = editFileService.editFile(testFile.toString(), edits, false);
        JsonNode jsonResult = objectMapper.readTree(result);

        // Then
        assertFalse(jsonResult.get("success").asBoolean());
        
        // Verify file was not modified
        assertEquals(initialContent, Files.readString(testFile));
    }
}