package com.boukensha.examples;

import com.boukensha.Agent;
import com.boukensha.Boukensha;
import com.boukensha.api.Client;
import com.boukensha.api.backend.Backend;
import com.boukensha.config.Config;
import com.boukensha.logger.SessionLogger;
import com.boukensha.model.Context;
import com.boukensha.model.PromptBuilder;
import com.boukensha.tasks.PlayerTask;
import com.boukensha.tool.Registry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Step 5 — The agent loop. The client is called repeatedly: tool calls are
 * dispatched and fed back until the model stops asking for tools.
 */
public class Step05AgentLoop {
  public static void main(String[] args) throws Exception {
    Config config = new Config();
    Map<String, Object> player = Boukensha.playerSettings(config);
    PlayerTask task = PlayerTask.INSTANCE;
    String systemPrompt = task.systemPrompt(player, config.getUserPromptsDir(), config.getPromptsDir());

    Path baseDir = Paths.get("").toAbsolutePath();
    Context context = new Context(systemPrompt);
    Registry registry = new Registry(context);

    registry.tool("read_file", "Read the contents of a file from disk",
        Map.of("path", Map.of("type", "string", "description", "The file path to read")),
        toolArgs -> {
          try {
            return Files.readString(baseDir.resolve(String.valueOf(toolArgs.get("path"))));
          } catch (Exception e) {
            return "error: " + e.getMessage();
          }
        });

    registry.tool("list_directory", "List the files in a directory",
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

    String provider = task.provider(player);
    String model = task.model(player);
    int maxIterations = task.maxIterations(player);
    int maxOutputTokens = task.maxOutputTokens(player);

    Backend backend = Boukensha.buildBackend(provider, Boukensha.apiKeyFor(config, provider), model,
        "http://localhost:11434");
    PromptBuilder builder = new PromptBuilder(context, backend);
    Client client = new Client(builder);
    SessionLogger logger = new SessionLogger(null, config.getDir() + "/sessions", null,
        Map.of("task", task.taskName(), "model", model, "provider", provider));

    context.addMessage("user",
        "Read the README.md file and summarise what this MUD player assistant framework can do.");

    System.out.println("=== BOUKENSHA Step 5: Agent Loop ===");
    System.out.println();
    System.out.println("Provider: " + provider);
    System.out.println("Model: " + model);
    System.out.println("Max iterations: " + maxIterations);
    System.out.println("Max output tokens: " + maxOutputTokens);
    System.out.println();

    try {
      Agent agent = new Agent(context, registry, builder, client, logger,
          maxIterations, null, maxOutputTokens);
      String result = agent.run();
      System.out.println();
      System.out.println("=== FINAL RESPONSE ===");
      System.out.println(result);
    } finally {
      logger.close();
    }
  }
}
