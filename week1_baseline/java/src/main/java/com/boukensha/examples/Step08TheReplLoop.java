package com.boukensha.examples;

import com.boukensha.Boukensha;
import com.boukensha.Models;
import com.boukensha.Version;
import com.boukensha.api.Client;
import com.boukensha.api.backend.Backend;
import com.boukensha.config.Config;
import com.boukensha.logger.SessionLogger;
import com.boukensha.model.Context;
import com.boukensha.model.PromptBuilder;
import com.boukensha.repl.Repl;
import com.boukensha.tasks.PlayerTask;
import com.boukensha.tool.Registry;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Step 8 — The REPL loop. Same primitives as one Boukensha.run call, but the
 * loop stays alive and the Context is shared across turns, so conversation
 * history accumulates.
 */
public class Step08TheReplLoop {
  public static void main(String[] args) throws Exception {
    start();
  }

  /** Shared by step 9, which reaches the same REPL through the global launcher. */
  static void start() throws Exception {
    Config config = Boukensha.config();
    Map<String, Object> player = Boukensha.playerSettings(config);
    PlayerTask task = PlayerTask.INSTANCE;

    String systemPrompt = task.systemPrompt(player, config.getUserPromptsDir(), config.getPromptsDir());
    String provider = task.provider(player);
    String model = task.model(player);
    String apiKey = Boukensha.apiKeyFor(config, provider);
    String workingDir = Paths.get("").toAbsolutePath().toString();

    Context context = new Context(systemPrompt, Models.contextWindow(model), workingDir,
        config.getAgentCompactionThreshold());
    Registry registry = new Registry(context);

    // Same set Ruby's Boukensha.repl registers from its working_dir:/mud: keywords:
    // filesystem + shell rooted here, plus the MUD tools when a host is configured.
    // Without the MUD tools the banner advertises a connection the agent can't use.
    Boukensha.Options options = new Boukensha.Options();
    options.workingDir = workingDir;
    Boukensha.registerStandardTools(registry, config, options);

    Backend backend = Boukensha.buildBackend(provider, apiKey, model, "http://localhost:11434");
    PromptBuilder builder = new PromptBuilder(context, backend);
    Client client = new Client(builder);

    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("task", task.taskName());
    snapshot.put("model", model);
    snapshot.put("provider", provider);
    SessionLogger logger = new SessionLogger(null, config.getDir() + "/sessions", null, snapshot);

    Map<String, Object> mud = new LinkedHashMap<>();
    mud.put("host", config.getMudHost());
    mud.put("port", config.getMudPort());
    mud.put("name", config.getMudUsername());

    Repl repl = new Repl(context, registry, builder, client, logger,
        config.getDir(), provider, model, Version.VERSION, apiKey, mud,
        config.getAgentMaxIterations(), config.getAgentMaxTurnTokens(),
        config.getAgentMaxOutputTokens());

    try {
      repl.start();
    } finally {
      logger.close();
    }
  }
}
