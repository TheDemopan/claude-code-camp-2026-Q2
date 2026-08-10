package com.boukensha.examples;

import com.boukensha.Boukensha;
import com.boukensha.config.Config;
import com.boukensha.model.Context;
import com.boukensha.model.Message;
import com.boukensha.tasks.PlayerTask;
import com.boukensha.tool.Tool;
import java.util.Map;

/**
 * Step 1 — The struct skeleton. Context, Message, and Tool exist; tools are
 * registered directly on the context (the Registry arrives in step 2).
 */
public class Step01StructSkeleton {
  public static void main(String[] args) throws Exception {
    Config config = new Config();
    Map<String, Object> player = Boukensha.playerSettings(config);
    String systemPrompt = PlayerTask.INSTANCE.systemPrompt(
        player, config.getUserPromptsDir(), config.getPromptsDir());

    Context context = new Context(systemPrompt);

    context.registerTool(new Tool(
        "move",
        "Move the player in a direction (north, south, east, west, up, down)",
        Map.of("direction", Map.of("type", "string", "description", "The direction to move")),
        toolArgs -> "You move " + toolArgs.get("direction") + " into a torch-lit corridor."));

    context.addMessage("user", "Explore north and tell me what you find.");

    System.out.println("=== Boukensha Step 1: Struct Skeleton ===");
    System.out.println();
    System.out.println("Context:  " + context);
    System.out.println("System:   " + (systemPrompt == null ? "(none)" : systemPrompt.lines().findFirst().orElse("")));
    System.out.println();
    System.out.println("Tools registered (" + context.getToolCount() + "):");
    for (Tool tool : context.getTools().values()) {
      System.out.println("  " + tool);
    }
    System.out.println();
    System.out.println("Messages (" + context.getTurnCount() + "):");
    for (Message message : context.getMessages()) {
      System.out.println("  " + message);
    }
    System.out.println();
    System.out.println("Dispatching 'move' directly off the tool struct:");
    System.out.println("  " + context.getTools().get("move").invoke(Map.of("direction", "north")));
  }
}
