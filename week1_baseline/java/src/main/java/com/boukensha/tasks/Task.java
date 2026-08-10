package com.boukensha.tasks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Port of Tasks::Base. Steps 00-11 read provider, model, prompts and limits
 * through a task; step 12 moves the same lookups onto Config directly.
 */
public class Task {
  public static final int DEFAULT_MAX_ITERATIONS = 25;
  public static final int DEFAULT_MAX_OUTPUT_TOKENS = 1024;

  private final String taskName;

  protected Task(String taskName) {
    this.taskName = taskName;
  }

  public String taskName() {
    return taskName;
  }

  public String provider(Map<String, Object> settings) {
    String value = fetch(settings, "provider");
    if (value == null) {
      throw new IllegalArgumentException(
          "tasks." + taskName + ".provider is required in settings.yaml");
    }
    return value;
  }

  public String model(Map<String, Object> settings) {
    String value = fetch(settings, "model");
    if (value == null) {
      throw new IllegalArgumentException(
          "tasks." + taskName + ".model is required in settings.yaml");
    }
    return value;
  }

  @SuppressWarnings("unchecked")
  public boolean promptOverride(Map<String, Object> settings, String prompt) {
    if (settings == null) {
      return false;
    }
    Object node = settings.get("prompt_override");
    if (!(node instanceof Map)) {
      return false;
    }
    return Boolean.TRUE.equals(((Map<String, Object>) node).get(prompt));
  }

  public String systemPrompt(Map<String, Object> settings, String userPromptsDir, String defaultPromptsDir)
      throws IOException {
    return prompt(settings, "system", userPromptsDir, defaultPromptsDir);
  }

  /**
   * A user override at <userPromptsDir>/<taskName>/<name>.md wins when the task
   * opts in; otherwise the bundled <defaultPromptsDir>/<name>.md is used.
   * Returns null when neither exists, matching Ruby.
   */
  public String prompt(Map<String, Object> settings, String name, String userPromptsDir, String defaultPromptsDir)
      throws IOException {
    if (promptOverride(settings, name) && userPromptsDir != null) {
      String text = readFile(Paths.get(userPromptsDir, taskName, name + ".md"));
      if (text != null) {
        return text;
      }
    }
    if (defaultPromptsDir == null) {
      return null;
    }
    return readFile(Paths.get(defaultPromptsDir, name + ".md"));
  }

  public int maxIterations(Map<String, Object> settings) {
    return intSetting(settings, "max_iterations", DEFAULT_MAX_ITERATIONS);
  }

  public int maxOutputTokens(Map<String, Object> settings) {
    return intSetting(settings, "max_output_tokens", DEFAULT_MAX_OUTPUT_TOKENS);
  }

  private static String fetch(Map<String, Object> settings, String key) {
    if (settings == null) {
      return null;
    }
    Object value = settings.get(key);
    if (value == null || String.valueOf(value).isBlank()) {
      return null;
    }
    return String.valueOf(value);
  }

  private static int intSetting(Map<String, Object> settings, String key, int fallback) {
    String value = fetch(settings, key);
    return value == null ? fallback : Integer.parseInt(value.trim());
  }

  private static String readFile(Path path) throws IOException {
    return Files.exists(path) ? Files.readString(path).strip() : null;
  }
}
