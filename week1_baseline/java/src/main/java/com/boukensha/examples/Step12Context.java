package com.boukensha.examples;

import com.boukensha.Boukensha;
import com.boukensha.Models;
import com.boukensha.config.Config;
import com.boukensha.tools.MudTools;

/**
 * Step 12 — Context management.
 *
 * Same MUD demo as step 10, but the run is bounded by the agent limits from
 * settings.yaml: a per-turn token ceiling alongside the iteration ceiling, and a
 * compaction threshold that drops the oldest messages when the context window
 * fills. The window itself is a model fact looked up from the Models table, not
 * a user setting.
 */
public class Step12Context {
  public static void main(String[] args) {
    Config config = Boukensha.config();
    String model = config.getModel();

    System.out.println("=== BOUKENSHA Step 12: Context Management ===");
    System.out.println();
    System.out.println("Model:                " + model);
    System.out.println("Context window:       " + Models.contextWindow(model) + " tokens");
    System.out.println("Max iterations:       " + config.getAgentMaxIterations());
    System.out.println("Max turn tokens:      " + config.getAgentMaxTurnTokens());
    System.out.println("Max output tokens:    " + config.getAgentMaxOutputTokens());
    System.out.println("Compaction threshold: " + config.getAgentCompactionThreshold());
    System.out.println();

    Boukensha.Options options = new Boukensha.Options();
    options.maxIterations = config.getAgentMaxIterations();
    options.maxTurnTokens = config.getAgentMaxTurnTokens();
    options.maxOutputTokens = config.getAgentMaxOutputTokens();

    String result = Boukensha.run(
        "Look around, check your score and your inventory, then summarise your situation "
            + "in a few sentences.",
        options,
        dsl -> MudTools.register(dsl.registry(),
            config.getMudHost(), config.getMudPort(),
            config.getMudUsername(), config.getMudPassword()));

    System.out.println();
    System.out.println("=== FINAL RESPONSE ===");
    System.out.println(result);
  }
}
