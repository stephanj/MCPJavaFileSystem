package com.devoxx.mcp.filesystem.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BashServiceTest {

    private BashService bashService;
    
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        bashService = new BashService();
    }

    @Test
    void shouldExecuteSimpleCommand() {
        // When
        String result = bashService.executeBash("echo 'Hello World'", null, null);
        
        // Then
        assertTrue(result.contains("Hello World"));
        assertTrue(result.contains("\"exitCode\":0"));
        assertTrue(result.contains("\"success\":true"));
    }
    
    @Test
    void shouldHandleWorkingDirectory() throws IOException {
        // Given
        File testFile = new File(tempDir.toFile(), "test.txt");
        Files.writeString(testFile.toPath(), "test content");
        
        // When
        String result = bashService.executeBash("cat test.txt", tempDir.toString(), null);
        
        // Then
        assertTrue(result.contains("test content"));
        assertTrue(result.contains("\"exitCode\":0"));
    }
    
    @Test
    void shouldHandleCommandTimeout() {
        // When
        String result = bashService.executeBash("sleep 3", null, 1);
        
        // Then
        assertTrue(result.contains("timed out"));
        assertTrue(result.contains("\"success\":false"));
    }
    
    @Test
    void shouldHandleCommandFailure() {
        // When
        String result = bashService.executeBash("ls /nonexistent_directory", null, null);
        
        // Then
        assertTrue(result.contains("\"exitCode\":"));
        assertFalse(result.contains("\"exitCode\":0"));
    }
    
    @Test
    void shouldHandleEmptyCommand() {
        // When
        String result = bashService.executeBash("", null, null);
        
        // Then
        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("Command cannot be empty"));
    }
    
    @Test
    void shouldCaptureCommandOutput() {
        // When
        String result = bashService.executeBash("echo 'Line 1' && echo 'Line 2'", null, null);
        
        // Then
        assertTrue(result.contains("Line 1"));
        assertTrue(result.contains("Line 2"));
        assertTrue(result.contains("\"exitCode\":0"));
    }
}