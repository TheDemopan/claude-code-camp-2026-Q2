package com.boukensha;

import com.boukensha.api.Client;
import com.boukensha.api.backend.AnthropicBackend;
import com.boukensha.api.backend.Backend;
import com.boukensha.api.backend.GeminiBackend;
import com.boukensha.api.backend.OllamaBackend;
import com.boukensha.api.backend.OllamaCloudBackend;
import com.boukensha.api.backend.OpenAIBackend;
import com.boukensha.config.Config;
import com.boukensha.dsl.RunDSL;
import com.boukensha.logger.SessionLogger;
import com.boukensha.model.Context;
import com.boukensha.model.PromptBuilder;
import com.boukensha.tasks.PlayerTask;
import com.boukensha.tool.Registry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Module-level entry point, mirroring the Boukensha module in Ruby: shared
 * config, the quiet/debug flags, and the one-shot run helper.
 */
public final class Boukensha {
  private static Config config;
  private static boolean quiet;
  private static boolean debug;

  private Boukensha() {
  }

  public static synchronized Config config() {
    if (config == null) {
      config = new Config();
    }
    return config;
  }

  /** Test seam: drop the memoized config so the next call re-reads the dir. */
  public static synchronized void resetConfig() {
    config = null;
  }

  public static void quiet() {
    quiet = true;
  }

  public static void loud() {
    quiet = false;
  }

  public static boolean isQuiet() {
    return quiet;
  }

  public static void debug() {
    debug = true;
  }

  public static boolean isDebug() {
    return debug;
  }

  /** Options for {@link #run}. All fields fall back to config when null. */
  public static class Options {
    public String system;
    public String model;
    public String backend;
    public String apiKey;
    public String ollamaHost = "http://localhost:11434";
    public String log;
    public Integer maxIterations;
    public Integer maxOutputTokens;
    public Integer maxTurnTokens;

    /**
     * Roots filesystem and shell tools at this directory. Defaults to the current
     * directory; set false to opt out entirely (Ruby's working_dir: false).
     */
    public String workingDir;
    public boolean fileTools = true;

    /**
     * Registers the MUD gameplay tools when settings.yaml has a mud host.
     * Set false to skip (Ruby's mud: false).
     */
    public boolean mudTools = true;
  }

  /**
   * Registers the standard tool libraries the way Ruby's run/repl do from their
   * working_dir: and mud: keywords: filesystem + shell rooted at the working
   * directory, and the MUD tools whenever a host is configured.
   */
  public static void registerStandardTools(Registry registry, Config cfg, Options options) {
    if (options.fileTools) {
      String dir = options.workingDir != null
          ? options.workingDir
          : java.nio.file.Paths.get("").toAbsolutePath().toString();
      com.boukensha.tools.FileSystemTools.register(registry, dir);
      com.boukensha.tools.ShellTools.register(registry, dir);
    }
    // mudTools true means "use config if a host is set" — matching Ruby, where
    // mud: nil falls back to settings.yaml and mud: false skips entirely.
    if (options.mudTools && cfg.getMudHost() != null && !cfg.getMudHost().isBlank()) {
      com.boukensha.tools.MudTools.register(registry, cfg.getMudHost(), cfg.getMudPort(),
          cfg.getMudUsername(), cfg.getMudPassword());
    }
  }

  /** One-shot run: send a single task, get a response back, return. */
  public static String run(String task, RunDSL.Block block) {
    return run(task, new Options(), block);
  }

  public static String run(String task, Options options, RunDSL.Block block) {
    Config cfg = config();
    Map<String, Object> taskSettings = playerSettings(cfg);
    PlayerTask playerTask = PlayerTask.INSTANCE;

    String system = options.system;
    if (system == null) {
      try {
        system = playerTask.systemPrompt(taskSettings, cfg.getUserPromptsDir(), cfg.getPromptsDir());
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    String model = options.model != null ? options.model : playerTask.model(taskSettings);
    String backendName = options.backend != null ? options.backend : playerTask.provider(taskSettings);
    String apiKey = options.apiKey != null ? options.apiKey : apiKeyFor(cfg, backendName);

    int maxIterations = options.maxIterations != null
        ? options.maxIterations : playerTask.maxIterations(taskSettings);
    int maxOutputTokens = options.maxOutputTokens != null
        ? options.maxOutputTokens : playerTask.maxOutputTokens(taskSettings);
    Integer maxTurnTokens = options.maxTurnTokens;

    Context context = new Context(system, Models.contextWindow(model), options.workingDir,
        cfg.getAgentCompactionThreshold());
    Registry registry = new Registry(context);
    registerStandardTools(registry, cfg, options);
    if (block != null) {
      block.define(new RunDSL(registry));
    }

    Backend backend = buildBackend(backendName, apiKey, model, options.ollamaHost);
    PromptBuilder builder = new PromptBuilder(context, backend);
    Client client = new Client(builder);

    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("task", playerTask.taskName());
    snapshot.put("max_iterations", maxIterations);
    snapshot.put("max_output_tokens", maxOutputTokens);
    snapshot.put("model", model);
    snapshot.put("provider", backendName);

    SessionLogger logger;
    try {
      logger = new SessionLogger(null, cfg.getDir() + "/sessions", options.log, snapshot);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    logger.setDebug(debug);

    try {
      Agent agent = new Agent(context, registry, builder, client, logger,
          maxIterations, maxTurnTokens, maxOutputTokens);
      context.addMessage("user", task);
      return agent.run();
    } finally {
      logger.close();
    }
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> playerSettings(Config cfg) {
    Object tasks = cfg.dig("tasks", "player");
    return tasks instanceof Map ? (Map<String, Object>) tasks : Map.of();
  }

  /** Resolves the provider's API key from the config dir's .env or the environment. */
  public static String apiKeyFor(Config cfg, String backend) {
    switch (backend) {
      case "anthropic":
        return cfg.env("ANTHROPIC_API_KEY");
      case "openai":
        return cfg.env("OPENAI_API_KEY");
      case "gemini":
        return cfg.env("GEMINI_API_KEY");
      case "ollama_cloud":
        return cfg.env("OLLAMA_API_KEY");
      default:
        return null;
    }
  }

  public static Backend buildBackend(String backend, String apiKey, String model, String ollamaHost) {
    switch (backend) {
      case "anthropic":
        return new AnthropicBackend(requireKey(apiKey, "ANTHROPIC_API_KEY"), model);
      case "openai":
        return new OpenAIBackend(requireKey(apiKey, "OPENAI_API_KEY"), model);
      case "gemini":
        return new GeminiBackend(requireKey(apiKey, "GEMINI_API_KEY"), model);
      case "ollama":
        return new OllamaBackend(ollamaHost, model);
      case "ollama_cloud":
        return new OllamaCloudBackend(requireKey(apiKey, "OLLAMA_API_KEY"), model);
      default:
        throw new IllegalArgumentException("Unknown backend " + backend
            + ". Use anthropic, openai, gemini, ollama, or ollama_cloud.");
    }
  }

  private static String requireKey(String apiKey, String name) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException(name + " is not set (expected in "
          + config().getDir() + "/.env or the environment).");
    }
    return apiKey;
  }
}
