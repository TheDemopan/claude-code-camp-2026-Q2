package com.boukensha.examples;

import com.boukensha.Boukensha;
import com.boukensha.Models;
import com.boukensha.Version;
import com.boukensha.api.Client;
import com.boukensha.api.backend.Backend;
import com.boukensha.config.Config;
import com.boukensha.logger.SessionLogger;
import com.boukensha.memory.MemoryRecorder;
import com.boukensha.memory.MemoryStore;
import com.boukensha.model.Context;
import com.boukensha.model.PromptBuilder;
import com.boukensha.repl.Repl;
import com.boukensha.tasks.PlayerTask;
import com.boukensha.tool.Registry;
import com.boukensha.tools.MemoryTools;
import com.boukensha.tools.MudTools;
import com.boukensha.tui.Tui;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Step 13 — Persistent memory (extra credit; not part of the graded 00–12 set).
 *
 * A memory-enabled REPL. Everything steps 08/11 do, plus:
 *
 *   · rooms and exits recorded automatically as you move (MemoryRecorder)
 *   · four memory files under week1_baseline/java/memory/ (MemoryStore)
 *   · find_route, map_summary, recall_room, set_objective, remember_note
 *   · a bounded memory brief prepended to the system prompt at startup
 *
 * Deliberately additive: no file used by steps 00–12 is modified, so the graded
 * launchers keep their exact behaviour. `bin/boukensha` remains memory-free;
 * `bin/13_memory` is this build.
 */
public class Step13Memory {

  public static void main(String[] args) throws Exception {
    Config config = Boukensha.config();
    Map<String, Object> player = Boukensha.playerSettings(config);
    PlayerTask task = PlayerTask.INSTANCE;

    String basePrompt = task.systemPrompt(player, config.getUserPromptsDir(), config.getPromptsDir());
    String provider = task.provider(player);
    String model = task.model(player);
    String apiKey = Boukensha.apiKeyFor(config, provider);
    String workingDir = Paths.get("").toAbsolutePath().toString();

    // Memory lives inside the Java port, self-contained.
    Path memoryDir = Paths.get(workingDir, "memory");
    MemoryStore store = new MemoryStore(memoryDir);

    // The brief goes into the system prompt so prior knowledge is available from
    // the first turn — bounded, never the whole map.
    String systemPrompt = (basePrompt == null ? "" : basePrompt) + "\n\n" + store.contextBrief();

    Context context = new Context(systemPrompt, Models.contextWindow(model), workingDir,
        config.getAgentCompactionThreshold());
    Registry registry = new Registry(context);

    // Order matters: MudTools first, then the decorator wraps look/move.
    MudTools.register(registry, config.getMudHost(), config.getMudPort(),
        config.getMudUsername(), config.getMudPassword());
    MemoryRecorder.wrap(registry, context, store, "Midgaard");
    MemoryTools.register(registry, store);

    Backend backend = Boukensha.buildBackend(provider, apiKey, model, "http://localhost:11434");
    PromptBuilder builder = new PromptBuilder(context, backend);
    Client client = new Client(builder);

    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("task", task.taskName());
    snapshot.put("model", model);
    snapshot.put("provider", provider);
    snapshot.put("memory_dir", memoryDir.toString());
    SessionLogger logger = new SessionLogger(null, config.getDir() + "/sessions", null, snapshot);

    Map<String, Object> mud = new LinkedHashMap<>();
    mud.put("host", config.getMudHost());
    mud.put("port", config.getMudPort());
    mud.put("name", config.getMudUsername());

    System.out.println("[memory] " + memoryDir);
    System.out.println("[memory] " + store.graph().size() + " rooms known"
        + (store.state().currentRoom == null ? ""
            : ", last seen in room " + store.state().currentRoom));

    Repl repl = new Repl(context, registry, builder, client, logger,
        config.getDir(), provider, model, Version.VERSION, apiKey, mud,
        config.getAgentMaxIterations(), config.getAgentMaxTurnTokens(),
        config.getAgentMaxOutputTokens());

    try {
      new Tui(repl, context).start(logger);
    } finally {
      store.save();
      logger.close();
    }
  }
}
