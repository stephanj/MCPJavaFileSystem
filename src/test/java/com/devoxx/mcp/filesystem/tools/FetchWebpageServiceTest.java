package com.devoxx.mcp.filesystem.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FetchWebpageServiceTest {

    private FetchWebpageService fetchWebpageService;
    private ObjectMapper objectMapper;

    @Mock
    private Connection connectionMock;
    
    @Mock
    private Document documentMock;

    @BeforeEach
    void setUp() {
        fetchWebpageService = new FetchWebpageService();
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldFetchWebpageSuccessfully() throws Exception {
        // Given
        String url = "https://example.com";
        String expectedTitle = "Example Domain";
        String expectedContent = "This domain is for use in illustrative examples";
        
        try (MockedStatic<Jsoup> jsoupMock = Mockito.mockStatic(Jsoup.class)) {
            jsoupMock.when(() -> Jsoup.connect(anyString())).thenReturn(connectionMock);
            when(connectionMock.timeout(anyInt())).thenReturn(connectionMock);
            when(connectionMock.get()).thenReturn(documentMock);
            when(documentMock.title()).thenReturn(expectedTitle);
            when(documentMock.text()).thenReturn(expectedContent);
            
            // When
            String result = fetchWebpageService.fetchWebpage(url, 5000);
            JsonNode jsonResult = objectMapper.readTree(result);
            
            // Then
            assertTrue(jsonResult.get("success").asBoolean());
            assertEquals(url, jsonResult.get("url").asText());
            assertEquals(expectedTitle, jsonResult.get("title").asText());
            assertEquals(expectedContent, jsonResult.get("content").asText());
        }
    }

    @Test
    void shouldUseDefaultTimeoutWhenNotProvided() throws Exception {
        // Given
        String url = "https://example.com";
        
        try (MockedStatic<Jsoup> jsoupMock = Mockito.mockStatic(Jsoup.class)) {
            jsoupMock.when(() -> Jsoup.connect(anyString())).thenReturn(connectionMock);
            // Verify that the default timeout (10000ms) is used when not specified
            when(connectionMock.timeout(10000)).thenReturn(connectionMock);
            when(connectionMock.get()).thenReturn(documentMock);
            when(documentMock.title()).thenReturn("Title");
            when(documentMock.text()).thenReturn("Content");
            
            // When
            String result = fetchWebpageService.fetchWebpage(url, null);
            JsonNode jsonResult = objectMapper.readTree(result);
            
            // Then
            assertTrue(jsonResult.get("success").asBoolean());
        }
    }

    @Test
    void shouldHandleIOException() throws Exception {
        // Given
        String url = "https://invalid-url.example";
        
        try (MockedStatic<Jsoup> jsoupMock = Mockito.mockStatic(Jsoup.class)) {
            jsoupMock.when(() -> Jsoup.connect(anyString())).thenReturn(connectionMock);
            when(connectionMock.timeout(anyInt())).thenReturn(connectionMock);
            when(connectionMock.get()).thenThrow(new IOException("Connection refused"));
            
            // When
            String result = fetchWebpageService.fetchWebpage(url, 5000);
            JsonNode jsonResult = objectMapper.readTree(result);
            
            // Then
            assertFalse(jsonResult.get("success").asBoolean());
            assertTrue(jsonResult.get("error").asText().contains("Failed to access url"));
        }
    }
    
    @Test
    void shouldHandleUnexpectedException() throws Exception {
        // Given
        String url = "https://example.com";
        
        try (MockedStatic<Jsoup> jsoupMock = Mockito.mockStatic(Jsoup.class)) {
            jsoupMock.when(() -> Jsoup.connect(anyString())).thenReturn(connectionMock);
            when(connectionMock.timeout(anyInt())).thenReturn(connectionMock);
            when(connectionMock.get()).thenThrow(new RuntimeException("Unexpected error"));
            
            // When
            String result = fetchWebpageService.fetchWebpage(url, 5000);
            JsonNode jsonResult = objectMapper.readTree(result);
            
            // Then
            assertFalse(jsonResult.get("success").asBoolean());
            assertTrue(jsonResult.get("error").asText().contains("Failed to access url"));
        }
    }
    
    @Test
    void shouldHandleNullUrl() throws Exception {
        // When
        String result = fetchWebpageService.fetchWebpage(null, 5000);
        JsonNode jsonResult = objectMapper.readTree(result);
        
        // Then
        assertFalse(jsonResult.get("success").asBoolean());
        assertTrue(jsonResult.get("error").asText().contains("Failed to access"));
    }
    
    @Test
    void shouldHandleEmptyUrl() throws Exception {
        // When
        String result = fetchWebpageService.fetchWebpage("", 5000);
        JsonNode jsonResult = objectMapper.readTree(result);
        
        // Then
        assertFalse(jsonResult.get("success").asBoolean());
        assertTrue(jsonResult.get("error").asText().contains("Failed"));
    }
}