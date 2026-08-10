package com.boukensha.examples;

import com.boukensha.Boukensha;
import com.boukensha.config.Config;
import com.boukensha.exception.UnknownToolError;
import com.boukensha.model.Context;
import com.boukensha.tasks.PlayerTask;
import com.boukensha.tool.Registry;
import com.boukensha.tool.Tool;
import java.util.Map;

/**
 * Step 2 — The registry. Tools are registered through a Registry rather than
 * directly on the context; the registry still attaches them to the context,
 * which is why it takes one at construction. It also owns dispatch.
 */
public class Step02TheRegistry {
  public static void main(String[] args) throws Exception {
    Config config = new Config();
    Map<String, Object> player = Boukensha.playerSettings(config);
    String systemPrompt = PlayerTask.INSTANCE.systemPrompt(
        player, config.getUserPromptsDir(), config.getPromptsDir());

    Context context = new Context(systemPrompt);
    Registry registry = new Registry(context);

    registry.tool("move",
        "Move the player in a direction (north, south, east, west, up, down)",
        Map.of("direction", Map.of("type", "string")),
        toolArgs -> "You move " + toolArgs.get("direction") + " into a torch-lit corridor.");

    registry.tool("shout",
        "Shout a message so everyone nearby hears it",
        Map.of("message", Map.of("type", "string")),
        toolArgs -> "You shout: " + toolArgs.get("message"));

    System.out.println("=== Boukensha Step 2: The Registry ===");
    System.out.println();
    System.out.println("Registered " + context.getToolCount() + " tools on " + context);
    for (Tool tool : context.getTools().values()) {
      System.out.println("  " + tool);
    }

    System.out.println();
    System.out.println("Dispatch through the registry:");
    System.out.println("  move  -> " + registry.dispatch("move", Map.of("direction", "north")));
    System.out.println("  shout -> " + registry.dispatch("shout", Map.of("message", "Hello!")));

    System.out.println();
    System.out.println("Unknown tools raise instead of failing silently:");
    try {
      registry.dispatch("teleport", Map.of());
    } catch (UnknownToolError e) {
      System.out.println("  " + e.getClass().getSimpleName() + ": " + e.getMessage());
    }
  }
}
