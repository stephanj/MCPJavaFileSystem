package com.devoxx.mcp.filesystem.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ListDirectoryServiceTest {

    private ListDirectoryService listDirectoryService;
    private ObjectMapper objectMapper;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        listDirectoryService = new ListDirectoryService();
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldListDirectoryContents() throws Exception {
        // Given
        // Create a test directory structure
        Path file1 = tempDir.resolve("file1.txt");
        Path file2 = tempDir.resolve("file2.log");
        Path subDir = tempDir.resolve("subdir");
        
        Files.writeString(file1, "Content of file 1");
        Files.writeString(file2, "Content of file 2");
        Files.createDirectory(subDir);
        
        // When
        String result = listDirectoryService.listDirectory(tempDir.toString());
        JsonNode jsonResult = objectMapper.readTree(result);
        
        // Then
        assertTrue(jsonResult.get("success").asBoolean());
        assertEquals(tempDir.toString(), jsonResult.get("path").asText());
        assertEquals(3, jsonResult.get("count").asInt());
        
        // Verify entries
        JsonNode entries = jsonResult.get("entries");
        assertTrue(entries.isArray());
        
        // Collect entry names and types for easier verification
        List<Map<String, String>> entryDetails = new ArrayList<>();
        for (JsonNode entry : entries) {
            String name = entry.get("name").asText();
            String type = entry.get("type").asText();
            entryDetails.add(Map.of("name", name, "type", type));
        }
        
        // Check if all our created files/dirs are in the results
        assertTrue(entryDetails.contains(Map.of("name", "file1.txt", "type", "FILE")));
        assertTrue(entryDetails.contains(Map.of("name", "file2.log", "type", "FILE")));
        assertTrue(entryDetails.contains(Map.of("name", "subdir", "type", "DIR")));
    }

    @Test
    void shouldSortDirectoriesFirst() throws Exception {
        // Given
        // Create a mixed set of files and directories with names that would be out of order alphabetically
        Path fileA = tempDir.resolve("a_file.txt");
        Path fileB = tempDir.resolve("b_file.txt");
        Path dirZ = tempDir.resolve("z_dir");
        Path dirY = tempDir.resolve("y_dir");
        
        Files.writeString(fileA, "Content A");
        Files.writeString(fileB, "Content B");
        Files.createDirectory(dirZ);
        Files.createDirectory(dirY);
        
        // When
        String result = listDirectoryService.listDirectory(tempDir.toString());
        JsonNode jsonResult = objectMapper.readTree(result);
        JsonNode entries = jsonResult.get("entries");
        
        // Then
        // Directories should come first, then files, both alphabetically
        assertEquals("DIR", entries.get(0).get("type").asText());
        assertEquals("DIR", entries.get(1).get("type").asText());
        assertEquals("FILE", entries.get(2).get("type").asText());
        assertEquals("FILE", entries.get(3).get("type").asText());
        
        // Check alphabetical order within types
        assertEquals("y_dir", entries.get(0).get("name").asText());
        assertEquals("z_dir", entries.get(1).get("name").asText());
        assertEquals("a_file.txt", entries.get(2).get("name").asText());
        assertEquals("b_file.txt", entries.get(3).get("name").asText());
    }
    
    @Test
    void shouldHandleEmptyDirectory() throws Exception {
        // Given
        Path emptyDir = tempDir.resolve("empty-dir");
        Files.createDirectory(emptyDir);
        
        // When
        String result = listDirectoryService.listDirectory(emptyDir.toString());
        JsonNode jsonResult = objectMapper.readTree(result);
        
        // Then
        assertTrue(jsonResult.get("success").asBoolean());
        assertEquals(0, jsonResult.get("count").asInt());
        assertTrue(jsonResult.get("entries").isArray());
        assertEquals(0, jsonResult.get("entries").size());
    }
    
    @Test
    void shouldHandleNonExistentDirectory() throws Exception {
        // Given
        String nonExistentDir = tempDir.resolve("non-existent-dir").toString();
        
        // When
        String result = listDirectoryService.listDirectory(nonExistentDir);
        JsonNode jsonResult = objectMapper.readTree(result);
        
        // Then
        assertFalse(jsonResult.get("success").asBoolean());
        assertTrue(jsonResult.get("error").asText().contains("Path does not exist"));
    }
    
    @Test
    void shouldHandleFileAsDirectoryPath() throws Exception {
        // Given
        Path file = tempDir.resolve("not-a-directory.txt");
        Files.writeString(file, "This is a file, not a directory");
        
        // When
        String result = listDirectoryService.listDirectory(file.toString());
        JsonNode jsonResult = objectMapper.readTree(result);
        
        // Then
        assertFalse(jsonResult.get("success").asBoolean());
        assertTrue(jsonResult.get("error").asText().contains("Path is not a directory"));
    }
    
    @Test
    void shouldIncludeMetadataForEntries() throws Exception {
        // Given
        Path file = tempDir.resolve("file-with-metadata.txt");
        String content = "Content to check size";
        Files.writeString(file, content);
        
        // When
        String result = listDirectoryService.listDirectory(tempDir.toString());
        JsonNode jsonResult = objectMapper.readTree(result);
        
        // Then
        JsonNode entries = jsonResult.get("entries");
        
        // Find our specific file in the entries
        JsonNode fileEntry = null;
        for (JsonNode entry : entries) {
            if (entry.get("name").asText().equals("file-with-metadata.txt")) {
                fileEntry = entry;
                break;
            }
        }
        
        assertNotNull(fileEntry, "File entry should be found in results");
        assertEquals("FILE", fileEntry.get("type").asText());
        assertEquals(content.length(), fileEntry.get("size").asInt());
        assertTrue(fileEntry.has("lastModified"));
        assertTrue(fileEntry.get("lastModified").asLong() > 0);
    }
}