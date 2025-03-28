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

import static org.junit.jupiter.api.Assertions.*;

class SearchFilesServiceTest {

    private SearchFilesService searchFilesService;
    private ObjectMapper objectMapper;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        searchFilesService = new SearchFilesService();
        objectMapper = new ObjectMapper();
        
        // Create a standard directory structure for tests
        try {
            // Create files with different extensions
            Files.writeString(tempDir.resolve("file1.txt"), "Text file 1");
            Files.writeString(tempDir.resolve("file2.txt"), "Text file 2");
            Files.writeString(tempDir.resolve("document.pdf"), "PDF content");
            Files.writeString(tempDir.resolve("image.jpg"), "Image content");
            
            // Create subdirectories with files
            Path subDir1 = tempDir.resolve("subdir1");
            Files.createDirectory(subDir1);
            Files.writeString(subDir1.resolve("subfile1.txt"), "Subdir text file");
            Files.writeString(subDir1.resolve("subimage.png"), "Subdir image content");
            
            Path subDir2 = tempDir.resolve("subdir2");
            Files.createDirectory(subDir2);
            Files.writeString(subDir2.resolve("another.txt"), "Another text file");
            
            // Create nested subdirectory
            Path nestedDir = subDir1.resolve("nested");
            Files.createDirectory(nestedDir);
            Files.writeString(nestedDir.resolve("deep-file.txt"), "Deeply nested file");
        } catch (Exception e) {
            fail("Failed to set up test directory structure: " + e.getMessage());
        }
    }

    @Test
    void shouldFindFilesByExtension() throws Exception {
        // When
        String result = searchFilesService.searchFiles(tempDir.toString(), "*.txt");
        JsonNode jsonResult = objectMapper.readTree(result);
        
        // Then
        assertTrue(jsonResult.get("success").asBoolean());
        JsonNode matches = jsonResult.get("matches");
        assertTrue(matches.isArray());
        
        // Should find all 5 .txt files
        assertEquals(5, jsonResult.get("count").asInt());
        
        // Extract just the filenames from the full paths for easier assertion
        List<String> fileNames = new ArrayList<>();
        for (JsonNode match : matches) {
            String path = match.asText();
            fileNames.add(Path.of(path).getFileName().toString());
        }
        
        assertTrue(fileNames.contains("file1.txt"));
        assertTrue(fileNames.contains("file2.txt"));
        assertTrue(fileNames.contains("subfile1.txt"));
        assertTrue(fileNames.contains("deep-file.txt"));
    }

    @Test
    void shouldFindFilesByPartialName() throws Exception {
        // When
        String result = searchFilesService.searchFiles(tempDir.toString(), "*file*");
        JsonNode jsonResult = objectMapper.readTree(result);
        
        // Then
        assertTrue(jsonResult.get("success").asBoolean());
        
        // Extract just the filenames from the full paths
        List<String> fileNames = new ArrayList<>();
        for (JsonNode match : jsonResult.get("matches")) {
            String path = match.asText();
            fileNames.add(Path.of(path).getFileName().toString());
        }
        
        assertTrue(fileNames.contains("file1.txt"));
        assertTrue(fileNames.contains("file2.txt"));
        assertTrue(fileNames.contains("subfile1.txt"));
        assertTrue(fileNames.contains("deep-file.txt"));
    }

    @Test
    void shouldFindDirectories() throws Exception {
        // When
        String result = searchFilesService.searchFiles(tempDir.toString(), "*dir*");
        JsonNode jsonResult = objectMapper.readTree(result);
        
        // Then
        assertTrue(jsonResult.get("success").asBoolean());
        
        // Extract just the directory names from the full paths
        List<String> dirNames = new ArrayList<>();
        for (JsonNode match : jsonResult.get("matches")) {
            String path = match.asText();
            dirNames.add(Path.of(path).getFileName().toString());
        }
        
        assertTrue(dirNames.contains("subdir1"));
        assertTrue(dirNames.contains("subdir2"));
    }
    
    @Test
    void shouldHandleNestedDirectories() throws Exception {
        // When - search in a subdirectory
        String result = searchFilesService.searchFiles(tempDir.resolve("subdir1").toString(), "*.txt");
        JsonNode jsonResult = objectMapper.readTree(result);
        
        // Then
        assertTrue(jsonResult.get("success").asBoolean());
        
        // Should find the text file in subdir1 and its nested directory
        assertEquals(2, jsonResult.get("count").asInt());
        
        List<String> fileNames = new ArrayList<>();
        for (JsonNode match : jsonResult.get("matches")) {
            String path = match.asText();
            fileNames.add(Path.of(path).getFileName().toString());
        }
        
        assertTrue(fileNames.contains("subfile1.txt"));
        assertTrue(fileNames.contains("deep-file.txt"));
    }
    
    @Test
    void shouldHandleNonExistentDirectory() throws Exception {
        // When
        String result = searchFilesService.searchFiles(tempDir.resolve("non-existent").toString(), "*.txt");
        JsonNode jsonResult = objectMapper.readTree(result);
        
        // Then
        assertFalse(jsonResult.get("success").asBoolean());
        assertTrue(jsonResult.get("error").asText().contains("Path does not exist"));
    }
    
    @Test
    void shouldHandleInvalidGlobPattern() throws Exception {
        // When
        String result = searchFilesService.searchFiles(tempDir.toString(), "[invalid-pattern");
        JsonNode jsonResult = objectMapper.readTree(result);
        
        // Then
        assertFalse(jsonResult.get("success").asBoolean());
        assertTrue(jsonResult.get("error").asText().contains("Invalid pattern syntax"));
    }
    
    @Test
    void shouldReturnEmptyResultsWhenNoMatches() throws Exception {
        // When
        String result = searchFilesService.searchFiles(tempDir.toString(), "nomatch*.xyz");
        JsonNode jsonResult = objectMapper.readTree(result);
        
        // Then
        assertTrue(jsonResult.get("success").asBoolean());
        assertEquals(0, jsonResult.get("count").asInt());
        assertTrue(jsonResult.get("matches").isArray());
        assertEquals(0, jsonResult.get("matches").size());
    }

    @Test
    void findResultsWithComplexWildcard() throws Exception {
//        "arguments": {
//            "path": "/Users/stephan/IdeaProjects/JavaFileSystemMCP",
//                    "pattern": "*.java*Test"
//        }
        // TODO
    }
}