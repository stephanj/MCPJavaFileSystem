package com.devoxx.mcp.filesystem.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
public class FetchWebpageService {

    private final ObjectMapper mapper = new ObjectMapper();

    @Tool(description = """
    Fetch or read a webpage from a URL and returns its HTML content. The service uses jsoup to connect to the 
    webpage and retrieve its content. Options include retrieving just the text content, filtering by CSS 
    selectors, and setting a timeout for the connection.
    """)
    public String fetchWebpage(
            @ToolParam(description = "The URL of the webpage to fetch or read") String url,
            @ToolParam(description = "Optional: Connection timeout in milliseconds (default: 10000)", required = false) Integer timeoutMs) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Set default timeout if not provided
            int timeout = (timeoutMs != null) ? timeoutMs : 10000;

            // Connect to the URL and get the document
            Document doc = Jsoup.connect(url)
                    .timeout(timeout)
                    .get();
            
            String content;
            
            content = doc.text();

            result.put("success", true);
            result.put("url", url);
            result.put("content", content);
            result.put("title", doc.title());
            
            return mapper.writeValueAsString(result);
            
        } catch (IOException e) {
            result.put("success", false);
            result.put("error", "Failed to fetch webpage: " + e.getMessage());
            result.put("url", url);
            try {
                return mapper.writeValueAsString(result);
            } catch (Exception ex) {
                return "{\"success\": false, \"error\": \"Failed to serialize error result\"}";
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "Unexpected error: " + e.getMessage());
            result.put("url", url);
            try {
                return mapper.writeValueAsString(result);
            } catch (Exception ex) {
                return "{\"success\": false, \"error\": \"Failed to serialize error result\"}";
            }
        }
    }
}