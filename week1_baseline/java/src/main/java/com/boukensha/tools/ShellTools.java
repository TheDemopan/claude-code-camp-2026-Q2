package com.boukensha.tools;

import com.boukensha.tool.Registry;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Registers command-execution tools against a registry.
 *
 * Commands run with the given working directory as their cwd and are killed
 * after a timeout. When allowedCommands is non-null, any command whose first
 * token is not in the list is rejected before execution.
 */
public final class ShellTools {
  private static final int DEFAULT_TIMEOUT_SECONDS = 30;

  private ShellTools() {
  }

  public static void register(Registry registry, String workingDir) {
    register(registry, workingDir, DEFAULT_TIMEOUT_SECONDS, null);
  }

  public static void register(Registry registry, String workingDir, int timeoutSeconds,
                              List<String> allowedCommands) {
    File root = Paths.get(workingDir).toAbsolutePath().normalize().toFile();

    String description = "Run a shell command inside the working directory and return its combined "
        + "stdout+stderr output. Commands run with a " + timeoutSeconds + "-second timeout."
        + (allowedCommands == null ? "" : " Allowed executables: " + String.join(", ", allowedCommands) + ".");

    registry.tool("run_command", description,
        Map.of("command", Map.of("type", "string",
            "description", "The shell command to execute (e.g. 'ls -la', 'git status')")),
        args -> {
          String command = args.get("command") == null ? "" : String.valueOf(args.get("command"));

          // Guard: check the first token against the allow-list when one is set.
          if (allowedCommands != null) {
            String trimmed = command.strip();
            String executable = trimmed.isEmpty() ? "" : trimmed.split("\\s+")[0];
            if (!allowedCommands.contains(executable)) {
              return "error: '" + executable + "' is not in the allowed-commands list ("
                  + String.join(", ", allowedCommands) + ")";
            }
          }

          return execute(command, root, timeoutSeconds);
        });
  }

  private static String execute(String command, File root, int timeoutSeconds) {
    Process process;
    try {
      // Ruby's capture2e runs a single command string through the shell; match that.
      process = new ProcessBuilder("/bin/sh", "-c", command)
          .directory(root)
          .redirectErrorStream(true)
          .start();
    } catch (IOException e) {
      return "error: command not found: " + e.getMessage();
    }

    String output;
    try (InputStream in = process.getInputStream()) {
      // Read before waiting so a chatty command cannot fill the pipe and deadlock.
      byte[] bytes = in.readAllBytes();
      output = new String(bytes, StandardCharsets.UTF_8);
    } catch (IOException e) {
      process.destroyForcibly();
      return "error: " + e.getMessage();
    }

    boolean finished;
    try {
      finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      process.destroyForcibly();
      return "error: interrupted while running: " + command;
    }

    if (!finished) {
      process.destroyForcibly();
      return "error: command timed out after " + timeoutSeconds + "s: " + command;
    }

    int exit = process.exitValue();
    String exitNote = exit == 0 ? "" : "\n[exit " + exit + "]";
    String trimmed = output.strip();
    return trimmed.isEmpty() ? "(no output)" + exitNote : trimmed + exitNote;
  }
}
