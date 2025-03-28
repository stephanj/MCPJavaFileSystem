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

    @Nested
    @DisplayName("Basic Search Tests")
    class BasicSearchTests {

        @Test
        void shouldFindSimpleTextPattern() throws Exception {
            // Given
            createTestFiles(tempDir);
            String pattern = "important text";

            // When
            String result = grepFilesService.grepFiles(tempDir.toString(), pattern, null, false, null, null);
            JsonNode jsonResult = objectMapper.readTree(result);

            // Then
            assertTrue(jsonResult.get("success").asBoolean());
            assertTrue(jsonResult.get("results").size() > 0);
        }

        @Test
        void shouldRespectFileExtensionFilter() throws Exception {
            // Given
            createTestFiles(tempDir);
            String pattern = "sample";

            // When - search only in .txt files
            String result = grepFilesService.grepFiles(tempDir.toString(), pattern, ".txt", false, null, null);
            JsonNode jsonResult = objectMapper.readTree(result);

            // Then
            assertTrue(jsonResult.get("success").asBoolean());
            assertTrue(!jsonResult.get("results").isEmpty());

            // All matches should be in .txt files
            for (JsonNode matchText : jsonResult.get("results")) {
                if (matchText.asText().contains("(") && matchText.asText().contains("matches)")) {
                    assertTrue(matchText.asText().contains(".txt"));
                }
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
            String resultWithNullFilter = grepFilesService.grepFiles(tempDir.toString(), pattern, null, false, null, null);
            JsonNode jsonResultNullFilter = objectMapper.readTree(resultWithNullFilter);

            // When - extension filter is empty
            String resultWithEmptyFilter = grepFilesService.grepFiles(tempDir.toString(), pattern, "", false, null, null);
            JsonNode jsonResultEmptyFilter = objectMapper.readTree(resultWithEmptyFilter);

            // Then - both should search all files
            assertTrue(jsonResultNullFilter.get("success").asBoolean());
            assertTrue(jsonResultEmptyFilter.get("success").asBoolean());

            // The summary should have the same number of matches for both null and empty filter
            String summaryNull = jsonResultNullFilter.get("summary").asText();
            String summaryEmpty = jsonResultEmptyFilter.get("summary").asText();
            assertTrue(summaryNull.contains("Found") && summaryEmpty.contains("Found"));
            assertEquals(summaryNull, summaryEmpty);

            // We should have matches in files with different extensions
            boolean foundTxtFile = false;
            boolean foundXmlFile = false;

            for (JsonNode matchText : jsonResultNullFilter.get("results")) {
                String text = matchText.asText();
                if (text.contains(".txt")) foundTxtFile = true;
                if (text.contains(".xml")) foundXmlFile = true;
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
            String result = grepFilesService.grepFiles(tempDir.toString(), pattern, null, true, null, null);
            JsonNode jsonResult = objectMapper.readTree(result);

            // Then
            assertTrue(jsonResult.get("success").asBoolean());
            assertTrue(jsonResult.get("results").size() > 0);
        }

        @Test
        void shouldProvideContextLines() throws Exception {
            // Given
            createTestFiles(tempDir);
            String pattern = "important text";
            int contextLines = 2;

            // When
            String result = grepFilesService.grepFiles(tempDir.toString(), pattern, null, false, contextLines, null);
            JsonNode jsonResult = objectMapper.readTree(result);

            // Then
            assertTrue(jsonResult.get("success").asBoolean());

            // Check that context lines are included
            // The context is included in the results as text with line breaks
            boolean foundContext = false;
            for (JsonNode aResult : jsonResult.get("results")) {
                String text = aResult.asText();
                if (text.contains("→") && text.contains("important text")) {
                    foundContext = true;
                    break;
                }
            }
            assertTrue(foundContext, "Should include context lines with arrow symbol");
        }

    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        void shouldHandleNonExistentDirectory() throws Exception {
            // Given
            String nonExistentDir = tempDir.resolve("non-existent").toString();
            String pattern = "test";

            // When
            String result = grepFilesService.grepFiles(nonExistentDir, pattern, null, false, null, null);
            JsonNode jsonResult = objectMapper.readTree(result);

            // Then
            assertFalse(jsonResult.get("success").asBoolean());
            assertTrue(jsonResult.has("error"));
            assertTrue(jsonResult.get("error").asText().contains("Directory does not exist"));
        }

        @Test
        void shouldHandleInvalidPattern() throws Exception {
            // Given
            createTestFiles(tempDir);
            String invalidRegexPattern = "[";

            // When - trying to use the invalid pattern as regex
            String result = grepFilesService.grepFiles(tempDir.toString(), invalidRegexPattern, null, true, null, null);
            JsonNode jsonResult = objectMapper.readTree(result);

            // Then
            assertFalse(jsonResult.get("success").asBoolean());
            assertTrue(jsonResult.has("error"));
        }
    }

    // TODO Make a test with the above grep search arguments because it should return a result
    @Test
    void basicGrepSearch() throws Exception {
        createTestFiles(tempDir);

        GrepFilesService grepFilesService = new GrepFilesService();
        String result = grepFilesService.grepFiles(tempDir.toString(), "sample", ".java", false, null, 5);

        JsonNode jsonResult = objectMapper.readTree(result);

        String summary = jsonResult.get("summary").asText();

        assertTrue(summary.contains("Found 1 matches in 1 files"),
                "Summary should mention 1 found file");
    }
}
