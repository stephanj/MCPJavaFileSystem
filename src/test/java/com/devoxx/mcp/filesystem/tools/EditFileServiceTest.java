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

    @Nested
    @DisplayName("Basic Functionality Tests")
    class BasicFunctionalityTests {
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
    }

    @Nested
    @DisplayName("Input Format Tests")
    class InputFormatTests {
        
        @Test
        void shouldHandleSingleJsonObjectFormat() throws Exception {
            // Given
            String initialContent = "Test single object format";
            Path testFile = tempDir.resolve("single-object.txt");
            Files.writeString(testFile, initialContent);
            
            // Single JSON object format
            String edits = "{\"oldText\":\"single object\",\"newText\":\"JSON object\"}";

            // When
            String result = editFileService.editFile(testFile.toString(), edits, false);
            JsonNode jsonResult = objectMapper.readTree(result);

            // Then
            assertTrue(jsonResult.get("success").asBoolean());
            
            // Verify replacement worked
            String expectedContent = "Test JSON object format";
            assertEquals(expectedContent, Files.readString(testFile));
        }

        @Test
        void shouldHandleJsonArrayFormat() throws Exception {
            // Given
            String initialContent = "First text and second text";
            Path testFile = tempDir.resolve("json-array.txt");
            Files.writeString(testFile, initialContent);
            
            // JSON array format
            String edits = "[{\"oldText\":\"First\",\"newText\":\"1st\"},{\"oldText\":\"second\",\"newText\":\"2nd\"}]";

            // When
            String result = editFileService.editFile(testFile.toString(), edits, false);
            JsonNode jsonResult = objectMapper.readTree(result);

            // Then
            assertTrue(jsonResult.get("success").asBoolean());
            assertEquals(2, jsonResult.get("editsApplied").asInt());
            
            // Verify both replacements worked
            String expectedContent = "1st text and 2nd text";
            assertEquals(expectedContent, Files.readString(testFile));
        }

        @Test
        void shouldHandleSimpleTextFormat() throws Exception {
            // Given
            String initialContent = "Simple format test";
            Path testFile = tempDir.resolve("simple-format.txt");
            Files.writeString(testFile, initialContent);
            
            // Simple text format with ---- separator
            String edits = "Simple format----Better format";

            // When
            String result = editFileService.editFile(testFile.toString(), edits, false);
            JsonNode jsonResult = objectMapper.readTree(result);

            // Then
            assertTrue(jsonResult.get("success").asBoolean());
            
            // Verify replacement worked
            String expectedContent = "Better format test";
            assertEquals(expectedContent, Files.readString(testFile));
        }

        // TODO
        void shouldHandleMalformedEditsJson() throws Exception {
            // Given
            String initialContent = "Test content";
            Path testFile = tempDir.resolve("malformed-json.txt");
            Files.writeString(testFile, initialContent);
            
            String edits = "This is not valid JSON or Simple format";

            // When
            String result = editFileService.editFile(testFile.toString(), edits, false);
            JsonNode jsonResult = objectMapper.readTree(result);

            // Then
            assertFalse(jsonResult.get("success").asBoolean());
            assertEquals("This is not valid JSON or Simple format", jsonResult.get("debug_received_edits").asText());
            
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

    @Nested
    @DisplayName("Line Ending and Whitespace Tests")
    class LineEndingTests {
        
        @Test
        void shouldHandleDifferentLineEndings() throws Exception {
            // Given
            String initialContent = "Windows\r\nLine\rEndings\nMixed";
            Path testFile = tempDir.resolve("line-endings.txt");
            Files.writeString(testFile, initialContent);
            
            // Test different line endings in the edit
            String edits = "{\"oldText\":\"Windows\\r\\nLine\\rEndings\",\"newText\":\"Normalized Line Endings\"}";

            // When
            String result = editFileService.editFile(testFile.toString(), edits, false);
            JsonNode jsonResult = objectMapper.readTree(result);

            // Then
            assertTrue(jsonResult.get("success").asBoolean());
            
            // Verify replacement worked despite different line endings
            String expectedContent = "Normalized Line Endings\nMixed";
            assertEquals(expectedContent, Files.readString(testFile));
        }

        @Test
        void shouldHandleLeadingAndTrailingWhitespace() throws Exception {
            // Given
            String initialContent = "Text with    spaces    and tabs\t\t";
            Path testFile = tempDir.resolve("whitespace.txt");
            Files.writeString(testFile, initialContent);
            
            String edits = "{\"oldText\":\"spaces    and tabs\",\"newText\":\"minimal whitespace\"}";

            // When
            String result = editFileService.editFile(testFile.toString(), edits, false);
            JsonNode jsonResult = objectMapper.readTree(result);

            // Then
            assertTrue(jsonResult.get("success").asBoolean());
            
            // Verify replacement preserved whitespace correctly
            String expectedContent = "Text with    minimal whitespace\t\t";
            assertEquals(expectedContent, Files.readString(testFile));
        }
    }

    @Nested
    @DisplayName("Regex Support Tests")
    class RegexTests {
        
        @Test
        void shouldHandleSimpleFormatWithRegex() throws Exception {
            // Given
            String initialContent = "User123, User456, User789";
            Path testFile = tempDir.resolve("regex-simple-format.txt");
            Files.writeString(testFile, initialContent);
            
            // Simple text format with regex enabled
            String edits = "{\"oldText\":\"User[0-9]{3}\",\"newText\":\"Member\",\"useRegex\":\"true\"}";

            // When
            String result = editFileService.editFile(testFile.toString(), edits, false);
            JsonNode jsonResult = objectMapper.readTree(result);

            // Then
            assertTrue(jsonResult.get("success").asBoolean());
            
            // Verify only the first match was replaced
            String expectedContent = "Member, User456, User789";
            assertEquals(expectedContent, Files.readString(testFile));
        }
        
        @Test
        void shouldReplaceOnlyFirstRegexMatch() throws Exception {
            // Given
            String initialContent = "User123, User456, User789";
            Path testFile = tempDir.resolve("regex-basic.txt");
            Files.writeString(testFile, initialContent);
            
            // Use regex to match pattern - should only replace first occurrence
            String edits = "{\"oldText\":\"User\\\\d+\",\"newText\":\"Member\",\"useRegex\":\"true\"}";

            // When
            String result = editFileService.editFile(testFile.toString(), edits, false);
            JsonNode jsonResult = objectMapper.readTree(result);

            // Then
            assertTrue(jsonResult.get("success").asBoolean());
            
            // Verify only the first match was replaced, not all matches
            String expectedContent = "Member, User456, User789";
            assertEquals(expectedContent, Files.readString(testFile));
        }

        @Test
        void shouldHandleInvalidRegexPattern() throws Exception {
            // Given
            String initialContent = "Test invalid regex";
            Path testFile = tempDir.resolve("invalid-regex.txt");
            Files.writeString(testFile, initialContent);
            
            // Invalid regex pattern (unclosed parenthesis)
            String edits = "{\"oldText\":\"Test (invalid\",\"newText\":\"Fixed\",\"useRegex\":\"true\"}";

            // When
            String result = editFileService.editFile(testFile.toString(), edits, false);
            JsonNode jsonResult = objectMapper.readTree(result);

            // Then
            assertFalse(jsonResult.get("success").asBoolean());
            assertTrue(jsonResult.get("error").asText().contains("Invalid regex pattern"));
            
            // Verify file was not modified
            assertEquals(initialContent, Files.readString(testFile));
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {
        
        @Test
        void shouldHandleEmptyFile() throws Exception {
            // Given
            String initialContent = "";
            Path testFile = tempDir.resolve("empty-file.txt");
            Files.writeString(testFile, initialContent);
            
            String edits = "{\"oldText\":\"\",\"newText\":\"Added content\"}";

            // When
            String result = editFileService.editFile(testFile.toString(), edits, false);
            JsonNode jsonResult = objectMapper.readTree(result);

            // Then
            assertTrue(jsonResult.get("success").asBoolean());
            
            // Verify content was added to empty file
            String expectedContent = "Added content";
            assertEquals(expectedContent, Files.readString(testFile));
        }

        @Test
        void shouldHandleLargeFile() throws Exception {
            // Given
            StringBuilder largeContent = new StringBuilder();
            for (int i = 0; i < 10000; i++) {
                largeContent.append("Line ").append(i).append("\n");
            }
            
            Path testFile = tempDir.resolve("large-file.txt");
            Files.writeString(testFile, largeContent.toString());
            
            // Find and replace a specific line
            String edits = "{\"oldText\":\"Line 5000\",\"newText\":\"MODIFIED LINE\"}";

            // When
            String result = editFileService.editFile(testFile.toString(), edits, false);
            JsonNode jsonResult = objectMapper.readTree(result);

            // Then
            assertTrue(jsonResult.get("success").asBoolean());
            
            // Verify the specific line was replaced
            assertTrue(Files.readString(testFile).contains("MODIFIED LINE"));
        }

        @Test
        void shouldHandleSpecialCharacters() throws Exception {
            // Given
            String initialContent = "Special chars: áéíóú ñÑ 你好 こんにちは";
            Path testFile = tempDir.resolve("special-chars.txt");
            Files.writeString(testFile, initialContent);
            
            String edits = "{\"oldText\":\"áéíóú ñÑ\",\"newText\":\"Unicode Works!\"}";

            // When
            String result = editFileService.editFile(testFile.toString(), edits, false);
            JsonNode jsonResult = objectMapper.readTree(result);

            // Then
            assertTrue(jsonResult.get("success").asBoolean());
            
            // Verify unicode characters were handled correctly
            String expectedContent = "Special chars: Unicode Works! 你好 こんにちは";
            assertEquals(expectedContent, Files.readString(testFile));
        }
    }
}