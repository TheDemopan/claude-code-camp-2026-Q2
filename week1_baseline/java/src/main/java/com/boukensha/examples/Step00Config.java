package com.boukensha.examples;

import com.boukensha.Boukensha;
import com.boukensha.config.Config;
import com.boukensha.tasks.PlayerTask;
import java.util.Map;

/** Step 0 — Configuration. Loads settings.yaml and .env, prints what it found. */
public class Step00Config {
  public static void main(String[] args) throws Exception {
    Config config = new Config();
    Map<String, Object> player = Boukensha.playerSettings(config);
    PlayerTask task = PlayerTask.INSTANCE;

    System.out.println("=== Boukensha Step 0: Configuration ===");
    System.out.println();
    System.out.println("Config dir:      " + config.getDir());
    System.out.println("Tasks:           " + String.join(", ", tasksKeys(config)));
    System.out.println();
    System.out.println("-- player task --");
    System.out.println("Provider:        " + task.provider(player));
    System.out.println("Model:           " + task.model(player));
    System.out.println("Prompt override? " + task.promptOverride(player, "system"));

    String prompt = task.systemPrompt(player, config.getUserPromptsDir(), config.getPromptsDir());
    System.out.println("System prompt:   " + preview(prompt));
    System.out.println();
    System.out.println("MUD host:        " + config.getMudHost() + ":" + config.getMudPort());
    System.out.println("MUD user:        " + config.getMudUsername());
    System.out.println();
    System.out.println("API key set?     " + (config.env("ANTHROPIC_API_KEY") != null));
  }

  @SuppressWarnings("unchecked")
  private static java.util.Set<String> tasksKeys(Config config) {
    Object tasks = config.dig("tasks");
    return tasks instanceof Map ? ((Map<String, Object>) tasks).keySet() : java.util.Set.of();
  }

  private static String preview(String text) {
    if (text == null) {
      return "(none)";
    }
    return text.length() <= 60 ? text : text.substring(0, 60) + "...";
  }
}
