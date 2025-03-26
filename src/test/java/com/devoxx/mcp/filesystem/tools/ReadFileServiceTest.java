package com.devoxx.mcp.filesystem.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class ReadFileServiceTest {

    private ReadFileService readFileService;
    private ObjectMapper objectMapper;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        readFileService = new ReadFileService();
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldSuccessfullyReadFile() throws Exception {
        // Given
        String testContent = "This is test content\nLine 2\nLine 3";
        Path testFile = tempDir.resolve("test-file.txt");
        Files.writeString(testFile, testContent);

        // When
        String result = readFileService.readFile(testFile.toString());
        JsonNode jsonResult = objectMapper.readTree(result);

        // Then
        assertTrue(jsonResult.get("success").asBoolean());
        assertEquals(testContent, jsonResult.get("content").asText());
        assertEquals(testFile.toString(), jsonResult.get("path").asText());
        assertEquals(testContent.getBytes().length, jsonResult.get("size").asInt());
    }

    @Test
    void shouldHandleNonExistentFile() throws Exception {
        // Given
        String nonExistentPath = tempDir.resolve("non-existent-file.txt").toString();

        // When
        String result = readFileService.readFile(nonExistentPath);
        JsonNode jsonResult = objectMapper.readTree(result);

        // Then
        assertFalse(jsonResult.get("success").asBoolean());
        assertTrue(jsonResult.get("error").asText().contains("File does not exist"));
    }

    @Test
    void shouldHandleDirectory() throws Exception {
        // Given
        Path directory = tempDir.resolve("test-directory");
        Files.createDirectory(directory);

        // When
        String result = readFileService.readFile(directory.toString());
        JsonNode jsonResult = objectMapper.readTree(result);

        // Then
        assertFalse(jsonResult.get("success").asBoolean());
        assertTrue(jsonResult.get("error").asText().contains("Path is not a regular file"));
    }

    @Test
    void shouldHandleEmptyFile() throws Exception {
        // Given
        Path emptyFile = tempDir.resolve("empty-file.txt");
        Files.createFile(emptyFile);

        // When
        String result = readFileService.readFile(emptyFile.toString());
        JsonNode jsonResult = objectMapper.readTree(result);

        // Then
        assertTrue(jsonResult.get("success").asBoolean());
        assertEquals("", jsonResult.get("content").asText());
        assertEquals(0, jsonResult.get("size").asInt());
    }

    @Test
    void shouldHandleLargeFile() throws Exception {
        // Given
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            largeContent.append("Line ").append(i).append("\n");
        }
        Path largeFile = tempDir.resolve("large-file.txt");
        Files.writeString(largeFile, largeContent.toString());

        // When
        String result = readFileService.readFile(largeFile.toString());
        JsonNode jsonResult = objectMapper.readTree(result);

        // Then
        assertTrue(jsonResult.get("success").asBoolean());
        assertEquals(largeContent.toString(), jsonResult.get("content").asText());
    }

    @Test
    void shouldHandleSpecialCharacters() throws Exception {
        // Given
        String specialContent = "Special characters: çñüé€ñÑ\nSymbols: ©®™§±";
        Path specialFile = tempDir.resolve("special-chars.txt");
        Files.writeString(specialFile, specialContent);

        // When
        String result = readFileService.readFile(specialFile.toString());
        JsonNode jsonResult = objectMapper.readTree(result);

        // Then
        assertTrue(jsonResult.get("success").asBoolean());
        assertEquals(specialContent, jsonResult.get("content").asText());
    }
}