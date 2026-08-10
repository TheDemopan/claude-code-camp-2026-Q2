package com.boukensha.examples;

import com.boukensha.Boukensha;
import com.boukensha.api.backend.Backend;
import com.boukensha.config.Config;
import com.boukensha.model.Context;
import com.boukensha.model.PromptBuilder;
import com.boukensha.tasks.PlayerTask;
import com.boukensha.tool.Registry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

/**
 * Step 3 — The prompt builder. Backends turn the shared Context into each
 * provider's own wire format. Nothing is sent yet; this prints the payload.
 */
public class Step03PromptBuilder {
  public static void main(String[] args) throws Exception {
    Config config = new Config();
    Map<String, Object> player = Boukensha.playerSettings(config);
    PlayerTask task = PlayerTask.INSTANCE;
    String systemPrompt = task.systemPrompt(player, config.getUserPromptsDir(), config.getPromptsDir());

    Context context = new Context(systemPrompt);
    Registry registry = new Registry(context);

    registry.tool("look", "Look around the current room for details", Map.of(),
        toolArgs -> "A damp stone corridor stretches north. Torches flicker on the walls.");

    registry.tool("move",
        "Move the player in a direction (north, south, east, west, up, down)",
        Map.of("direction", Map.of("type", "string", "description", "The direction to move")),
        toolArgs -> "You move " + toolArgs.get("direction") + ".");

    context.addMessage("user", "Look around, then head north.");

    String provider = task.provider(player);
    String model = task.model(player);
    // No API key needed to build a payload — pass a placeholder so the backend
    // constructs without reaching for the environment.
    Backend backend = Boukensha.buildBackend(provider, "not-used-for-payload-building", model,
        "http://localhost:11434");

    PromptBuilder builder = new PromptBuilder(context, backend);
    ObjectMapper mapper = new ObjectMapper();

    System.out.println("=== Boukensha Step 3: The Prompt Builder ===");
    System.out.println();
    System.out.println("Provider: " + provider + "   Model: " + model);
    System.out.println("URL:      " + builder.getUrl());
    System.out.println("Headers:  " + builder.getHeaders().keySet());
    System.out.println();
    System.out.println("-- messages --");
    System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(builder.toMessages()));
    System.out.println();
    System.out.println("-- tools --");
    System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(builder.toTools()));
  }
}
