package com.boukensha.examples;

import com.boukensha.Boukensha;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Step 7 — The Boukensha.run DSL. Config, backend, client, logger, and agent
 * are all wired internally; the caller supplies a task and a tool block.
 *
 * Ruby uses instance_eval so the block sees `tool` as a bare method. Java has no
 * equivalent, so the block receives the RunDSL instance as a parameter.
 */
public class Step07TheRunDsl {
  public static void main(String[] args) {
    Path baseDir = Paths.get("").toAbsolutePath();

    System.out.println("=== BOUKENSHA Step 7: The Boukensha.run DSL ===");
    System.out.println();
    System.out.println("Config: " + Boukensha.config().getDir());
    System.out.println();

    String result = Boukensha.run(
        "Read the README.md file and summarise what this MUD player assistant framework can do.",
        dsl -> {
          dsl.tool("read_file", "Read the contents of a file from disk",
              Map.of("path", Map.of("type", "string", "description", "The file path to read")),
              toolArgs -> {
                try {
                  return Files.readString(baseDir.resolve(String.valueOf(toolArgs.get("path"))));
                } catch (Exception e) {
                  return "error: " + e.getMessage();
                }
              });

          dsl.tool("list_directory", "List the files in a directory",
              Map.of("path", Map.of("type", "string", "description", "The directory path to list")),
              toolArgs -> {
                try (var entries = Files.list(baseDir.resolve(String.valueOf(toolArgs.get("path"))))) {
                  return entries.map(p -> p.getFileName().toString())
                      .filter(n -> !n.startsWith("."))
                      .sorted()
                      .collect(Collectors.joining(", "));
                } catch (Exception e) {
                  return "error: " + e.getMessage();
                }
              });
        });

    System.out.println();
    System.out.println("=== FINAL RESPONSE ===");
    System.out.println(result);
  }
}
