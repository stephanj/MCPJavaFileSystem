package com.devoxx.mcp.filesystem;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.PatternSyntaxException;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class FileSystemService {

	@Tool(description = """
       Recursively search for files and directories matching a pattern. Searches through all subdirectories from the
       starting path. The search is case-insensitive and matches partial names. Returns full paths to all matching
       items. Great for finding files when you don't know their exact location.""")
	public String searchFiles(@ToolParam(description = "The base path to search in") String path,
							  @ToolParam(description = "The optional glob pattern to use, for example */**.java") String pattern) {

		Path basePath = Paths.get(path);
		ObjectMapper mapper = new ObjectMapper();
		Map<String, Object> result = new HashMap<>();

		if (!Files.exists(basePath)) {
			result.put("success", false);
			result.put("error", "Path does not exist: " + path);
			try {
				return mapper.writeValueAsString(result);
			} catch (Exception e) {
				return "{\"success\": false, \"error\": \"Failed to serialize result\"}";
			}
		}

		List<String> matchingPaths = new ArrayList<>();

		try {
			// Convert the pattern to a matcher
			final PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);

			// Walk the file tree
			Files.walkFileTree(basePath, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					if (pathMatcher.matches(basePath.relativize(file))) {
						matchingPaths.add(file.toString());
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(Path file, IOException exc) {
					System.err.println("Failed to access: " + file + " - " + exc.getMessage());
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
					if (pathMatcher.matches(basePath.relativize(dir))) {
						matchingPaths.add(dir.toString());
					}
					return FileVisitResult.CONTINUE;
				}
			});

			// Return the matched paths as JSON
			result.put("success", true);
			result.put("matches", matchingPaths);
			result.put("count", matchingPaths.size());

			try {
				return mapper.writeValueAsString(result);
			} catch (Exception e) {
				return "{\"success\": false, \"error\": \"Failed to serialize result\"}";
			}

		} catch (PatternSyntaxException e) {
			result.put("success", false);
			result.put("error", "Invalid pattern syntax: " + e.getMessage());
			try {
				return mapper.writeValueAsString(result);
			} catch (Exception ex) {
				return "{\"success\": false, \"error\": \"Failed to serialize error result\"}";
			}
		} catch (IOException e) {
			result.put("success", false);
			result.put("error", "Failed to search the file system: " + e.getMessage());
			try {
				return mapper.writeValueAsString(result);
			} catch (Exception ex) {
				return "{\"success\": false, \"error\": \"Failed to serialize error result\"}";
			}
		}
	}

	public static void main(String[] args) {
		FileSystemService fileSystemService = new FileSystemService();
		System.out.println(fileSystemService.searchFiles("/Users/stephan/IdeaProjects/DevoxxGenieIDEAPlugin", "**/*.java"));
	}
}