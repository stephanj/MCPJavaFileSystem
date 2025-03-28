package com.devoxx.mcp.filesystem.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CreateDirectoryServiceTest {

    private CreateDirectoryService createDirectoryService;
    private ObjectMapper objectMapper;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        createDirectoryService = new CreateDirectoryService();
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldSuccessfullyCreateSingleDirectory() throws Exception {
        // Given
        Path testDir = tempDir.resolve("test-directory");
        String testDirPath = testDir.toString();
        String[] directories = new String[] { testDirPath };

        // When
        String result = createDirectoryService.createDirectory(directories);
        JsonNode jsonResult = objectMapper.readTree(result);

        // Then
        assertTrue(jsonResult.get("success").asBoolean());
        assertTrue(Files.exists(testDir));
        assertTrue(Files.isDirectory(testDir));
    }

    @Test
    void shouldSuccessfullyCreateMultipleDirectories() throws Exception {
        // Given
        Path testDir1 = tempDir.resolve("test-dir-1");
        Path testDir2 = tempDir.resolve("test-dir-2");
        Path testDir3 = tempDir.resolve("test-dir-3");
        
        String[] directories = new String[] { 
            testDir1.toString(),
            testDir2.toString(),
            testDir3.toString()
        };

        // When
        String result = createDirectoryService.createDirectory(directories);
        JsonNode jsonResult = objectMapper.readTree(result);

        // Then
        assertTrue(jsonResult.get("success").asBoolean());
        assertTrue(Files.exists(testDir1));
        assertTrue(Files.exists(testDir2));
        assertTrue(Files.exists(testDir3));
        assertTrue(Files.isDirectory(testDir1));
        assertTrue(Files.isDirectory(testDir2));
        assertTrue(Files.isDirectory(testDir3));
    }

    @Test
    void shouldCreateNestedDirectoryStructure() throws Exception {
        // Given
        Path nestedDir = tempDir.resolve("parent/child/grandchild");
        String[] directories = new String[] { nestedDir.toString() };

        // When
        String result = createDirectoryService.createDirectory(directories);
        JsonNode jsonResult = objectMapper.readTree(result);

        // Then
        assertTrue(jsonResult.get("success").asBoolean());
        assertTrue(Files.exists(nestedDir));
        assertTrue(Files.isDirectory(nestedDir));
        assertTrue(Files.exists(nestedDir.getParent()));
        assertTrue(Files.exists(nestedDir.getParent().getParent()));
    }

    @Test
    void shouldSucceedWhenDirectoryAlreadyExists() throws Exception {
        // Given
        Path existingDir = tempDir.resolve("existing-directory");
        Files.createDirectory(existingDir);
        String[] directories = new String[] { existingDir.toString() };

        // When
        String result = createDirectoryService.createDirectory(directories);
        JsonNode jsonResult = objectMapper.readTree(result);

        // Then
        assertTrue(jsonResult.get("success").asBoolean());
        assertTrue(Files.exists(existingDir));
        assertTrue(Files.isDirectory(existingDir));
    }

    @Test
    void shouldHandleEmptyDirectoryList() throws Exception {
        // Given
        String[] directories = new String[0];

        // When
        String result = createDirectoryService.createDirectory(directories);
        JsonNode jsonResult = objectMapper.readTree(result);

        // Then
        assertTrue(jsonResult.get("success").asBoolean());
    }

    @Test
    void shouldHandleSpecialCharactersInDirectoryName() throws Exception {
        // Given
        Path specialNameDir = tempDir.resolve("special_char-dir-ñäöü");
        String[] directories = new String[] { specialNameDir.toString() };

        // When
        String result = createDirectoryService.createDirectory(directories);
        JsonNode jsonResult = objectMapper.readTree(result);

        // Then
        assertTrue(jsonResult.get("success").asBoolean());
        assertTrue(Files.exists(specialNameDir));
        assertTrue(Files.isDirectory(specialNameDir));
    }

    @Test
    void shouldHandlePathsWithSpaces() throws Exception {
        // Given
        Path dirWithSpaces = tempDir.resolve("dir with spaces in name");
        String[] directories = new String[] { dirWithSpaces.toString() };

        // When
        String result = createDirectoryService.createDirectory(directories);
        JsonNode jsonResult = objectMapper.readTree(result);

        // Then
        assertTrue(jsonResult.get("success").asBoolean());
        assertTrue(Files.exists(dirWithSpaces));
        assertTrue(Files.isDirectory(dirWithSpaces));
    }
}