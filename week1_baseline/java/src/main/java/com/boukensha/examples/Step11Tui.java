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
import com.boukensha.tools.MudTools;
import com.boukensha.tui.Tui;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Step 11 — The TUI. A live view of the agent working, driven by the logger's
 * event stream: iterations, tool calls and results, compaction, and per-turn
 * token spend appear as they happen rather than after the turn completes.
 *
 * See com.boukensha.tui.Tui — this is a reimplementation of the Ruby charm TUI,
 * not a port of it.
 */
public class Step11Tui {
  public static void main(String[] args) throws Exception {
    Config config = Boukensha.config();
    Map<String, Object> player = Boukensha.playerSettings(config);
    PlayerTask task = PlayerTask.INSTANCE;

    String systemPrompt = task.systemPrompt(player, config.getUserPromptsDir(), config.getPromptsDir());
    String provider = task.provider(player);
    String model = task.model(player);
    String apiKey = Boukensha.apiKeyFor(config, provider);

    Context context = new Context(systemPrompt, Models.contextWindow(model), null,
        config.getAgentCompactionThreshold());
    Registry registry = new Registry(context);
    MudTools.register(registry, config.getMudHost(), config.getMudPort(),
        config.getMudUsername(), config.getMudPassword());

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
      new Tui(repl, context).start(logger);
    } finally {
      logger.close();
    }
  }
}
