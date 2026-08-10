package com.boukensha.examples;

import com.boukensha.Agent;
import com.boukensha.api.Client;
import com.boukensha.api.backend.AnthropicBackend;
import com.boukensha.api.backend.Backend;
import com.boukensha.api.backend.GeminiBackend;
import com.boukensha.api.backend.OllamaBackend;
import com.boukensha.api.backend.OllamaCloudBackend;
import com.boukensha.api.backend.OpenAIBackend;
import com.boukensha.config.Config;
import com.boukensha.logger.SessionLogger;
import com.boukensha.model.Context;
import com.boukensha.model.PromptBuilder;
import com.boukensha.tool.Registry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Java equivalent of ruby/05_agent_loop/examples/example.rb.
 *
 * Reads provider/model from tasks.player in settings.yaml, registers two
 * filesystem tools, and runs one agent turn.
 */
public class Example {

  private static final int DEFAULT_MAX_ITERATIONS = 25;
  private static final int DEFAULT_MAX_OUTPUT_TOKENS = 1024;

  public static void main(String[] args) {
    try {
      run();
    } catch (IllegalStateException e) {
      System.err.println();
      System.err.println("Configuration error: " + e.getMessage());
      System.exit(1);
    } catch (Exception e) {
      System.err.println();
      System.err.println(e.getClass().getSimpleName() + ": " + e.getMessage());
      System.exit(1);
    }
  }

  private static void run() throws IOException {
    Config config = new Config();

    // tasks.player is required, the same way Tasks::Base demands it in Ruby.
    String provider = requiredSetting(config, "provider");
    String model = requiredSetting(config, "model");
    int maxIterations = intSetting(config, "max_iterations", DEFAULT_MAX_ITERATIONS);
    int maxOutputTokens = intSetting(config, "max_output_tokens", DEFAULT_MAX_OUTPUT_TOKENS);
    String systemPrompt = systemPrompt(config);

    // Tools resolve paths against the project directory, as base_dir does in Ruby.
    Path baseDir = Paths.get("").toAbsolutePath();

    Context context = new Context(systemPrompt);
    Registry registry = new Registry(context);

    registry.tool("read_file",
        "Read the contents of a file from disk",
        Map.of("path", Map.of("type", "string", "description", "The file path to read")),
        toolArgs -> {
          try {
            return Files.readString(baseDir.resolve(String.valueOf(toolArgs.get("path"))));
          } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
          }
        });

    registry.tool("list_directory",
        "List the files in a directory",
        Map.of("path", Map.of("type", "string", "description", "The directory path to list")),
        toolArgs -> {
          Path dir = baseDir.resolve(String.valueOf(toolArgs.get("path")));
          try (var entries = Files.list(dir)) {
            return entries.map(p -> p.getFileName().toString())
                .filter(name -> !name.startsWith("."))
                .sorted()
                .collect(Collectors.joining(", "));
          } catch (IOException e) {
            return "Error listing directory: " + e.getMessage();
          }
        });

    Backend backend = buildBackend(config, provider, model);
    PromptBuilder builder = new PromptBuilder(context, backend);
    Client client = new Client(builder);
    SessionLogger logger = new SessionLogger(null, config.getDir() + "/sessions", null,
        Map.of("task", "player", "provider", provider, "model", model,
               "max_iterations", maxIterations, "max_output_tokens", maxOutputTokens));
    Agent agent = new Agent(context, registry, builder, client, logger,
        maxIterations, null, maxOutputTokens);

    context.addMessage("user",
        "Read the README.md file and summarise what this MUD player assistant framework can do.");

    System.out.println("=== BOUKENSHA (Java): Agent Loop ===");
    System.out.println();
    System.out.println("Config dir: " + config.getDir());
    System.out.println("Provider: " + provider);
    System.out.println("Model: " + model);
    System.out.println("Max iterations: " + maxIterations);
    System.out.println("Max output tokens: " + maxOutputTokens);
    System.out.println();

    String result = agent.run();

    System.out.println();
    System.out.println("=== FINAL RESPONSE ===");
    System.out.println(result);
  }

  private static Backend buildBackend(Config config, String provider, String model) {
    switch (provider) {
      case "anthropic":
        return new AnthropicBackend(requiredEnv(config, "ANTHROPIC_API_KEY"), model);
      case "openai":
        return new OpenAIBackend(requiredEnv(config, "OPENAI_API_KEY"), model);
      case "gemini":
        return new GeminiBackend(requiredEnv(config, "GEMINI_API_KEY"), model);
      case "ollama":
        return new OllamaBackend(config.env("OLLAMA_HOST"), model);
      case "ollama_cloud":
        return new OllamaCloudBackend(requiredEnv(config, "OLLAMA_API_KEY"), model);
      default:
        throw new IllegalStateException("Unsupported provider for player task: " + provider
            + ". Use anthropic, openai, gemini, ollama, or ollama_cloud.");
    }
  }

  private static String requiredEnv(Config config, String name) {
    String value = config.env(name);
    if (value == null) {
      throw new IllegalStateException(
          name + " is not set (expected in " + config.getDir() + "/.env or the environment).");
    }
    return value;
  }

  private static String requiredSetting(Config config, String key) {
    Object value = config.dig("tasks", "player", key);
    if (value == null || String.valueOf(value).isBlank()) {
      throw new IllegalStateException(
          "tasks.player." + key + " is required in " + config.getDir() + "/settings.yaml");
    }
    return String.valueOf(value);
  }

  private static int intSetting(Config config, String key, int fallback) {
    Object value = config.dig("tasks", "player", key);
    if (value == null) {
      return fallback;
    }
    return Integer.parseInt(String.valueOf(value).trim());
  }

  /**
   * Mirrors Tasks::Base#system_prompt: a user override at
   * <configDir>/prompts/player/system.md wins when tasks.player.prompt_override.system
   * is true, otherwise the bundled prompts/system.md is used.
   */
  private static String systemPrompt(Config config) throws IOException {
    if (Boolean.TRUE.equals(config.dig("tasks", "player", "prompt_override", "system"))) {
      Path override = Paths.get(config.getUserPromptsDir(), "player", "system.md");
      if (Files.exists(override)) {
        return Files.readString(override).strip();
      }
    }

    Path bundled = Paths.get(config.getPromptsDir(), "system.md");
    if (!Files.exists(bundled)) {
      throw new IllegalStateException("System prompt not found at " + bundled
          + ". Run this via bin/boukensha so the working directory is the project root.");
    }
    return Files.readString(bundled).strip();
  }
}
