package com.devoxx.mcp.filesystem.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WriteFileServiceTest {

    private WriteFileService writeFileService;
    private ObjectMapper objectMapper;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        writeFileService = new WriteFileService();
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldSuccessfullyCreateNewFile() throws Exception {
        // Given
        String testContent = "This is new file content";
        Path testFile = tempDir.resolve("new-file.txt");
        String testFilePath = testFile.toString();

        // When
        String result = writeFileService.writeFile(testFilePath, testContent);
        JsonNode jsonResult = objectMapper.readTree(result);

        // Then
        assertTrue(jsonResult.get("success").asBoolean());
        assertEquals(testFilePath, jsonResult.get("path").asText());
        assertEquals("created", jsonResult.get("action").asText());
        assertEquals(testContent.getBytes().length, jsonResult.get("bytesWritten").asInt());
        
        // Verify file was actually created with correct content
        assertTrue(Files.exists(testFile));
        assertEquals(testContent, Files.readString(testFile));
    }

    @Test
    void shouldSuccessfullyOverwriteExistingFile() throws Exception {
        // Given
        String initialContent = "Initial content";
        String newContent = "New content that should overwrite the initial content";
        Path testFile = tempDir.resolve("existing-file.txt");
        Files.writeString(testFile, initialContent);
        String testFilePath = testFile.toString();

        // When
        String result = writeFileService.writeFile(testFilePath, newContent);
        JsonNode jsonResult = objectMapper.readTree(result);

        // Then
        assertTrue(jsonResult.get("success").asBoolean());
        assertEquals(testFilePath, jsonResult.get("path").asText());
        assertEquals("overwritten", jsonResult.get("action").asText());
        assertEquals(newContent.getBytes().length, jsonResult.get("bytesWritten").asInt());
        
        // Verify file was actually overwritten with new content
        assertEquals(newContent, Files.readString(testFile));
    }

    @Test
    void shouldCreateParentDirectories() throws Exception {
        // Given
        String testContent = "Content in nested directory";
        Path nestedDir = tempDir.resolve("parent/child/grandchild");
        Path testFile = nestedDir.resolve("nested-file.txt");
        String testFilePath = testFile.toString();

        // When
        String result = writeFileService.writeFile(testFilePath, testContent);
        JsonNode jsonResult = objectMapper.readTree(result);

        // Then
        assertTrue(jsonResult.get("success").asBoolean());
        assertEquals(testFilePath, jsonResult.get("path").asText());
        assertTrue(jsonResult.has("createdDirectories"));
        
        // Verify directories and file were created
        assertTrue(Files.exists(nestedDir));
        assertTrue(Files.exists(testFile));
        assertEquals(testContent, Files.readString(testFile));
    }

    @Test
    void shouldHandleEmptyContent() throws Exception {
        // Given
        String emptyContent = "";
        Path testFile = tempDir.resolve("empty-content.txt");
        String testFilePath = testFile.toString();

        // When
        String result = writeFileService.writeFile(testFilePath, emptyContent);
        JsonNode jsonResult = objectMapper.readTree(result);

        // Then
        assertTrue(jsonResult.get("success").asBoolean());
        assertEquals(0, jsonResult.get("bytesWritten").asInt());
        
        // Verify file was created but is empty
        assertTrue(Files.exists(testFile));
        assertEquals("", Files.readString(testFile));
    }

    @Test
    void shouldHandleSpecialCharacters() throws Exception {
        // Given
        String specialContent = "Special characters: çñüé€ñÑ\nSymbols: ©®™§±";
        Path testFile = tempDir.resolve("special-chars.txt");
        String testFilePath = testFile.toString();

        // When
        String result = writeFileService.writeFile(testFilePath, specialContent);
        JsonNode jsonResult = objectMapper.readTree(result);

        // Then
        assertTrue(jsonResult.get("success").asBoolean());
        
        // Verify file was created with special characters intact
        assertEquals(specialContent, Files.readString(testFile));
    }

    @Test
    void shouldHandleLargeContent() throws Exception {
        // Given
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            largeContent.append("Line ").append(i).append("\n");
        }
        Path testFile = tempDir.resolve("large-file.txt");
        String testFilePath = testFile.toString();

        // When
        String result = writeFileService.writeFile(testFilePath, largeContent.toString());
        JsonNode jsonResult = objectMapper.readTree(result);

        // Then
        assertTrue(jsonResult.get("success").asBoolean());
        assertEquals(largeContent.length(), jsonResult.get("bytesWritten").asInt());
        
        // Verify large file was created correctly
        assertEquals(largeContent.toString(), Files.readString(testFile));
    }
}