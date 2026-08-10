package com.boukensha.examples;

import com.boukensha.Boukensha;
import com.boukensha.api.Client;
import com.boukensha.api.backend.Backend;
import com.boukensha.config.Config;
import com.boukensha.model.Context;
import com.boukensha.model.PromptBuilder;
import com.boukensha.tasks.PlayerTask;
import com.boukensha.tool.Registry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

/**
 * Step 4 — The API client. Sends the built payload for real and prints the
 * parsed response. One request only; the agent loop arrives in step 5.
 */
public class Step04ApiClient {
  public static void main(String[] args) throws Exception {
    Config config = new Config();
    Map<String, Object> player = Boukensha.playerSettings(config);
    PlayerTask task = PlayerTask.INSTANCE;
    String systemPrompt = task.systemPrompt(player, config.getUserPromptsDir(), config.getPromptsDir());

    Context context = new Context(systemPrompt);
    Registry registry = new Registry(context);
    registry.tool("look", "Look around the current room for details", Map.of(),
        toolArgs -> "A damp stone corridor stretches north.");

    context.addMessage("user", "Say hello in one short sentence.");

    String provider = task.provider(player);
    String model = task.model(player);
    Backend backend = Boukensha.buildBackend(provider, Boukensha.apiKeyFor(config, provider), model,
        "http://localhost:11434");

    PromptBuilder builder = new PromptBuilder(context, backend);
    Client client = new Client(builder);

    System.out.println("=== Boukensha Step 4: The API Client ===");
    System.out.println();
    System.out.println("Provider: " + provider + "   Model: " + model);
    System.out.println("Sending one request...");
    System.out.println();

    Map<String, Object> response = client.call(Map.of("max_output_tokens", 256));

    System.out.println("-- raw response --");
    System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(response));
    System.out.println();
    System.out.println("-- normalized --");
    System.out.println(builder.parseResponse(response));
  }
}
