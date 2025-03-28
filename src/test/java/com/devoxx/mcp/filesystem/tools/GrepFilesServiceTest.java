package com.devoxx.mcp.filesystem.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GrepFilesServiceTest {

    private GrepFilesService grepFilesService;
    private ObjectMapper objectMapper;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        grepFilesService = new GrepFilesService();
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("Basic Search Tests")
    class BasicSearchTests {

        @Test
        void shouldFindSimpleTextPattern() throws Exception {
            // Given
            createTestFiles(tempDir);
            String pattern = "important text";

            // When
            String result = grepFilesService.grepFiles(tempDir.toString(), pattern, null, false, null, null, null);
            JsonNode jsonResult = objectMapper.readTree(result);

            // Then
            assertTrue(jsonResult.get("success").asBoolean());
            assertTrue(jsonResult.get("totalMatches").asInt() > 0);
            assertTrue(jsonResult.get("matches").isArray());
            assertEquals(1, jsonResult.get("matches").size());
        }

        @Test
        void shouldRespectFileExtensionFilter() throws Exception {
            // Given
            createTestFiles(tempDir);
            String pattern = "sample";

            // When - search only in .txt files
            String result = grepFilesService.grepFiles(tempDir.toString(), pattern, ".txt", false, null, null, null);
            JsonNode jsonResult = objectMapper.readTree(result);

            // Then
            assertTrue(jsonResult.get("success").asBoolean());
            assertTrue(jsonResult.get("totalMatches").asInt() > 0);
            
            // All matches should be in .txt files
            for (JsonNode fileMatch : jsonResult.get("matches")) {
                assertTrue(fileMatch.get("file").asText().endsWith(".txt"));
            }
        }
    }

    @Nested
    @DisplayName("Filter Tests")
    class FilterTests {
        
        @Test
        void shouldSearchAllFilesWhenExtensionFilterIsNull() throws Exception {
            // Given
            createTestFiles(tempDir);
            String pattern = "sample"; // Appears in both .txt and .xml files
            
            // When - extension filter is null
            String resultWithNullFilter = grepFilesService.grepFiles(tempDir.toString(), pattern, null, false, null, null, null);
            JsonNode jsonResultNullFilter = objectMapper.readTree(resultWithNullFilter);
            
            // When - extension filter is empty
            String resultWithEmptyFilter = grepFilesService.grepFiles(tempDir.toString(), pattern, "", false, null, null, null);
            JsonNode jsonResultEmptyFilter = objectMapper.readTree(resultWithEmptyFilter);
            
            // Then - both should search all files
            assertTrue(jsonResultNullFilter.get("success").asBoolean());
            assertTrue(jsonResultEmptyFilter.get("success").asBoolean());
            
            // The number of matches should be the same for both null and empty filter
            assertEquals(
                jsonResultNullFilter.get("totalMatches").asInt(),
                jsonResultEmptyFilter.get("totalMatches").asInt()
            );
            
            // We should have matches in files with different extensions
            boolean foundTxtFile = false;
            boolean foundXmlFile = false;
            
            for (JsonNode fileMatch : jsonResultNullFilter.get("matches")) {
                String filePath = fileMatch.get("file").asText();
                if (filePath.endsWith(".txt")) foundTxtFile = true;
                if (filePath.endsWith(".xml")) foundXmlFile = true;
            }
            
            assertTrue(foundTxtFile || foundXmlFile, "Should find matches in different file types");
        }
    }

    @Nested
    @DisplayName("Advanced Search Tests")
    class AdvancedSearchTests {

        @Test
        void shouldUseRegexPatternMatching() throws Exception {
            // Given
            createTestFiles(tempDir);
            // Regex to match "line" followed by a number
            String pattern = "line\\s+[0-9]+";

            // When
            String result = grepFilesService.grepFiles(tempDir.toString(), pattern, null, true, null, null, null);
            JsonNode jsonResult = objectMapper.readTree(result);

            // Then
            assertTrue(jsonResult.get("success").asBoolean());
            assertTrue(jsonResult.get("totalMatches").asInt() > 0);
        }

        @Test
        void shouldProvideContextLines() throws Exception {
            // Given
            createTestFiles(tempDir);
            String pattern = "important text";
            int contextLines = 2;

            // When
            String result = grepFilesService.grepFiles(tempDir.toString(), pattern, null, false, contextLines, null, null);
            JsonNode jsonResult = objectMapper.readTree(result);

            // Then
            assertTrue(jsonResult.get("success").asBoolean());
            
            // Check that context lines are included
            JsonNode matches = jsonResult.get("matches").get(0).get("matches").get(0);
            assertTrue(matches.has("contextBefore"));
            assertTrue(matches.has("contextAfter"));
        }

        @Test
        void shouldRespectIgnoreCase() throws Exception {
            // Given
            createTestFiles(tempDir);
            // Search for uppercase "IMPORTANT" when file contains lowercase "important"
            String pattern = "IMPORTANT";

            // When - case sensitive (should not find)
            String resultCaseSensitive = grepFilesService.grepFiles(tempDir.toString(), pattern, null, false, null, null, false);
            JsonNode jsonResultCaseSensitive = objectMapper.readTree(resultCaseSensitive);

            // When - case insensitive (should find)
            String resultCaseInsensitive = grepFilesService.grepFiles(tempDir.toString(), pattern, null, false, null, null, true);
            JsonNode jsonResultCaseInsensitive = objectMapper.readTree(resultCaseInsensitive);

            // Then
            assertTrue(jsonResultCaseSensitive.get("success").asBoolean());
            assertEquals(0, jsonResultCaseSensitive.get("totalMatches").asInt());
            
            assertTrue(jsonResultCaseInsensitive.get("success").asBoolean());
            assertTrue(jsonResultCaseInsensitive.get("totalMatches").asInt() > 0);
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        void shouldRespectMaxResultsLimit() throws Exception {
            // Given
            createTestFiles(tempDir);
            String pattern = "line"; // Should match multiple occurrences
            int maxResults = 1;

            // When
            String result = grepFilesService.grepFiles(tempDir.toString(), pattern, null, false, null, maxResults, null);
            JsonNode jsonResult = objectMapper.readTree(result);

            // Then
            assertTrue(jsonResult.get("success").asBoolean());
            assertEquals(maxResults, jsonResult.get("totalMatches").asInt());
            assertTrue(jsonResult.get("limitReached").asBoolean());
        }

        @Test
        void shouldHandleEmptyDirectory() throws Exception {
            // Given
            Path emptyDir = tempDir.resolve("empty-dir");
            Files.createDirectory(emptyDir);
            String pattern = "test";

            // When
            String result = grepFilesService.grepFiles(emptyDir.toString(), pattern, null, false, null, null, null);
            JsonNode jsonResult = objectMapper.readTree(result);

            // Then
            assertTrue(jsonResult.get("success").asBoolean());
            assertEquals(0, jsonResult.get("totalMatches").asInt());
            assertEquals(0, jsonResult.get("totalFiles").asInt());
            assertFalse(jsonResult.get("limitReached").asBoolean());
        }

        @Test
        void shouldHandleNonExistentDirectory() throws Exception {
            // Given
            String nonExistentDir = tempDir.resolve("non-existent-dir").toString();
            String pattern = "test";

            // When
            String result = grepFilesService.grepFiles(nonExistentDir, pattern, null, false, null, null, null);
            JsonNode jsonResult = objectMapper.readTree(result);

            // Then
            assertFalse(jsonResult.get("success").asBoolean());
            assertTrue(jsonResult.get("error").asText().contains("Directory does not exist"));
        }

        @Test
        void shouldHandleInvalidRegexPattern() throws Exception {
            // Given
            createTestFiles(tempDir);
            // Invalid regex pattern (unclosed parenthesis)
            String pattern = "test(unclosed";

            // When
            String result = grepFilesService.grepFiles(tempDir.toString(), pattern, null, true, null, null, null);
            JsonNode jsonResult = objectMapper.readTree(result);

            // Then
            assertFalse(jsonResult.get("success").asBoolean());
            assertTrue(jsonResult.get("error").asText().contains("Error searching files"));
        }
    }

    private void createTestFiles(Path directory) throws Exception {
        // Create a few test files with different content and extensions
        Path textFile1 = directory.resolve("file1.txt");
        Files.writeString(textFile1, "This is a sample text file\nWith multiple lines\nAnd some important text here\nline 1\nline 2");

        Path textFile2 = directory.resolve("file2.txt");
        Files.writeString(textFile2, "Another sample file\nWith different content\nBut no important keywords");

        Path javaFile = directory.resolve("Example.java");
        Files.writeString(javaFile, """
                public class Example {
                    // Sample Java code
                    public static void main(String[] args) {
                        System.out.println("Hello, World!");
                        // line 10 of code
                    }
                }
                """);
        
        Path xmlFile = directory.resolve("config.xml");
        Files.writeString(xmlFile, """
                <configuration>
                    <sample>value</sample>
                    <important>setting</important>
                </configuration>
                """);
    }
}