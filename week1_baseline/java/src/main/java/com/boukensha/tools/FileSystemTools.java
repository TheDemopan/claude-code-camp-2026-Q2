package com.boukensha.tools;

import com.boukensha.tool.Registry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Registers the standard file-oriented tools against a registry, all sandboxed
 * to a single root directory.
 *
 * Every path the agent supplies is resolved relative to that root. If the
 * resolved path would escape the root, the tool returns an error string rather
 * than throwing — the agent sees it and can try something sensible instead.
 *
 * list_directory and search_files are commented out in the Ruby original and
 * are intentionally not ported.
 */
public final class FileSystemTools {

  private FileSystemTools() {
  }

  public static void register(Registry registry, String workingDir) {
    Path root = Paths.get(workingDir).toAbsolutePath().normalize();

    registry.tool("pwd",
        "Return the working directory — the root that all file paths are relative to.",
        Map.of(),
        args -> root.toString());

    registry.tool("read_file",
        "Read and return the full contents of a file. Path is relative to the working directory.",
        Map.of("path", Map.of("type", "string", "description", "Relative path to the file")),
        args -> {
          String raw = String.valueOf(args.get("path"));
          Path target = resolve(root, raw);
          if (target == null) {
            return escapeError(raw);
          }
          if (!Files.isRegularFile(target)) {
            return "error: '" + raw + "' is not a file";
          }
          try {
            return Files.readString(target);
          } catch (IOException e) {
            return "error: " + e.getMessage();
          }
        });

    registry.tool("write_file",
        "Write content to a file, creating it (and any missing parent directories) if needed, "
            + "overwriting if it exists. Path is relative to the working directory.",
        Map.of(
            "path", Map.of("type", "string", "description", "Relative path to the file"),
            "content", Map.of("type", "string", "description", "Text content to write")),
        args -> {
          String raw = String.valueOf(args.get("path"));
          Path target = resolve(root, raw);
          if (target == null) {
            return escapeError(raw);
          }
          String content = args.get("content") == null ? "" : String.valueOf(args.get("content"));
          try {
            Path parent = target.getParent();
            if (parent != null) {
              Files.createDirectories(parent);
            }
            Files.writeString(target, content);
            int bytes = content.getBytes(StandardCharsets.UTF_8).length;
            return "ok: wrote " + bytes + " bytes to " + root.relativize(target);
          } catch (IOException e) {
            return "error: " + e.getMessage();
          }
        });

    registry.tool("delete_file",
        "Delete a file. Directories are not deleted. Path is relative to the working directory.",
        Map.of("path", Map.of("type", "string", "description", "Relative path to the file to delete")),
        args -> {
          String raw = String.valueOf(args.get("path"));
          Path target = resolve(root, raw);
          if (target == null) {
            return escapeError(raw);
          }
          if (!Files.isRegularFile(target)) {
            return "error: '" + raw + "' is not a file";
          }
          try {
            Files.delete(target);
            return "ok: deleted " + raw;
          } catch (IOException e) {
            return "error: " + e.getMessage();
          }
        });
  }

  /**
   * Resolve an agent-supplied path inside root. Returns null when the result
   * would escape the sandbox — the caller turns that into an error string.
   */
  static Path resolve(Path root, String path) {
    Path candidate = root.resolve(path == null ? "" : path).normalize().toAbsolutePath();
    return candidate.startsWith(root) ? candidate : null;
  }

  private static String escapeError(String path) {
    return "error: path '" + path + "' escapes the working directory";
  }
}
