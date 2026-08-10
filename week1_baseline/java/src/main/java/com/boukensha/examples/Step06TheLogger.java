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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Step 6 — The logger. Every phase of the turn is written to a JSONL session
 * file: iteration, prompt, tool_call, tool_result, response, turn_end.
 */
public class Step06TheLogger {
  public static void main(String[] args) throws Exception {
    Config config = new Config();
    Map<String, Object> player = Boukensha.playerSettings(config);
    PlayerTask task = PlayerTask.INSTANCE;
    String systemPrompt = task.systemPrompt(player, config.getUserPromptsDir(), config.getPromptsDir());

    Context context = new Context(systemPrompt);
    Registry registry = new Registry(context);
    registry.tool("roll_dice", "Roll an N-sided die and return the result",
        Map.of("sides", Map.of("type", "string", "description", "Number of sides")),
        toolArgs -> {
          int sides = Integer.parseInt(String.valueOf(toolArgs.get("sides")));
          return "You rolled a " + (1 + (int) (Math.random() * sides)) + " on a d" + sides + ".";
        });

    String provider = task.provider(player);
    String model = task.model(player);
    Backend backend = Boukensha.buildBackend(provider, Boukensha.apiKeyFor(config, provider), model,
        "http://localhost:11434");
    PromptBuilder builder = new PromptBuilder(context, backend);
    Client client = new Client(builder);

    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("task", task.taskName());
    snapshot.put("model", model);
    snapshot.put("provider", provider);
    SessionLogger logger = new SessionLogger(null, config.getDir() + "/sessions", null, snapshot);

    System.out.println("=== BOUKENSHA Step 6: The Logger ===");
    System.out.println();
    System.out.println("Session id:  " + logger.getSessionId());
    System.out.println("Session log: " + logger.getPath());
    System.out.println();

    context.addMessage("user", "Roll a 20-sided die and tell me what you got.");

    try {
      Agent agent = new Agent(context, registry, builder, client, logger,
          task.maxIterations(player), null, task.maxOutputTokens(player));
      String result = agent.run();
      System.out.println();
      System.out.println("=== FINAL RESPONSE ===");
      System.out.println(result);
    } finally {
      logger.close();
    }

    System.out.println();
    System.out.println("=== SESSION LOG (phases) ===");
    for (String line : Files.readAllLines(Path.of(logger.getPath()))) {
      int start = line.indexOf("\"phase\":\"");
      if (start >= 0) {
        int from = start + 9;
        System.out.println("  " + line.substring(from, line.indexOf('"', from)));
      }
    }
  }
}
