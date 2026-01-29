package com.devoxx.mcp.filesystem.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class BashService extends AbstractToolService {

    private static final int TIMEOUT_SECONDS = 30;
    private static final Set<String> DISALLOWED_COMMANDS =
            new HashSet<>(List.of("rm", "rmdir", "mv", "del", "erase", "dd", "mkfs", "format"));

    @Tool(description = """
        Execute a Bash command in the system shell and return the output.
        This tool allows running system commands and capturing their standard output and error streams.
        Use with caution as some commands may have system-wide effects. DO NOT USE REMOVE OR DELETE COMMANDS!
    """)
    public String executeBash(@ToolParam(description = "The Bash command to execute") String command,
                              @ToolParam(description = "Optional working directory for the command execution", required = false) String workingDirectory,
                              @ToolParam(description = "Maximum time in seconds to wait for the command to complete (default: 30)", required = false) Integer timeoutSeconds) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Validate input
            if (command == null || command.trim().isEmpty()) {
                return errorMessage("Command cannot be empty");
            }

            String commandName = command.split(" ")[0];
            if (DISALLOWED_COMMANDS.contains(commandName)) {
                return errorMessage("Command '" + commandName + "' is not allowed because it is potentially dangerous.");
            }

            // Set execution parameters
            ProcessBuilder processBuilder = new ProcessBuilder("/bin/bash", "-c", command);
            
            // Set working directory if provided
            if (workingDirectory != null && !workingDirectory.trim().isEmpty()) {
                processBuilder.directory(new java.io.File(workingDirectory));
            }
            
            // Merge standard output and error
            processBuilder.redirectErrorStream(true);
            
            Process process = processBuilder.start();
            
            int timeout = (timeoutSeconds != null && timeoutSeconds > 0) ? timeoutSeconds : TIMEOUT_SECONDS;
            boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);
            
            List<String> outputLines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    outputLines.add(line);
                }
            }
            
            // Check if the process completed or timed out
            if (!completed) {
                process.destroyForcibly();
                return errorMessage("Command execution timed out after " + timeout + " seconds");
            }
            
            // Get exit code
            int exitCode = process.exitValue();
            
            // Prepare the result
            result.put("command", command);
            result.put("exitCode", exitCode);
            result.put("output", String.join("\n", outputLines));
            
            return successMessage(result);
            
        } catch (IOException e) {
            return errorMessage("IO error occurred: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return errorMessage("Command execution was interrupted: " + e.getMessage());
        } catch (Exception e) {
            return errorMessage("Unexpected error: " + e.getMessage());
        }
    }
}
