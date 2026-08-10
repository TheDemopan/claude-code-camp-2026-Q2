package com.boukensha.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.yaml.snakeyaml.Yaml;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class Config {
  private static final String DEFAULT_DIR = System.getProperty("user.home") + "/.boukensha";
  private static final String PROMPTS_DIR = System.getProperty("user.dir") + "/prompts";

  private final String dir;
  private final Dotenv dotenv;
  private final Map<String, Object> settings;

  public Config() {
    this.dir = resolveDir();
    this.dotenv = loadEnv();
    this.settings = loadSettings();
  }

  public String getDir() {
    return dir;
  }

  public Map<String, Object> getSettings() {
    return settings;
  }

  public Object dig(Object... keys) {
    Object current = settings;
    for (Object key : keys) {
      if (current instanceof Map) {
        Map<String, Object> map = (Map<String, Object>) current;
        String keyStr = key.toString();
        current = map.get(keyStr);
        if (current == null) {
          return null;
        }
      } else {
        return null;
      }
    }
    return current;
  }

  public String getMudHost() {
    Object val = dig("mud", "host");
    return val != null ? val.toString() : "localhost";
  }

  public int getMudPort() {
    Object val = dig("mud", "port");
    return val != null ? Integer.parseInt(val.toString()) : 4000;
  }

  public String getMudUsername() {
    Object val = dig("mud", "username");
    return val != null ? val.toString() : null;
  }

  public String getMudPassword() {
    Object val = dig("mud", "password");
    return val != null ? val.toString() : null;
  }

  public String getUserPromptsDir() {
    return dir + "/prompts";
  }

  public String getPromptsDir() {
    return PROMPTS_DIR;
  }

  // ---------- provider (step 12) -----------------------------------------
  // Step 12 folds these lookups onto Config; steps 00-11 reach them through
  // Tasks::Player instead. Both surfaces are kept so every example maps 1:1.

  public String getProviderType() {
    Object value = dig("tasks", "player", "provider");
    return value != null ? String.valueOf(value) : "anthropic";
  }

  public String getModel() {
    Object value = dig("tasks", "player", "model");
    return value != null ? String.valueOf(value) : "claude-haiku-4-5";
  }

  public boolean isSystemOverride() {
    return Boolean.TRUE.equals(dig("system", "override"));
  }

  // ---------- agent limits ------------------------------------------------
  // Static per-turn circuit breakers. 0 or null means disabled (no ceiling).

  public int getAgentMaxIterations() {
    return intSetting(dig("agent", "max_iterations"), 25);
  }

  public int getAgentMaxOutputTokens() {
    return intSetting(dig("agent", "max_output_tokens"), 1024);
  }

  public int getAgentMaxTurnTokens() {
    return intSetting(dig("agent", "max_turn_tokens"), 60_000);
  }

  public double getAgentCompactionThreshold() {
    Object value = dig("agent", "compaction_threshold");
    return value == null ? 0.85 : Double.parseDouble(String.valueOf(value).trim());
  }

  /**
   * Resolves the system prompt the way step 12 does: a task-scoped override at
   * <dir>/prompts/player/system.md wins when tasks.player.prompt_override.system
   * is true, otherwise the flat <dir>/prompts/system.md is used. Returns null
   * when neither exists.
   */
  public String getSystemPrompt() throws IOException {
    if (Boolean.TRUE.equals(dig("tasks", "player", "prompt_override", "system"))) {
      String text = readIfExists(Paths.get(dir, "prompts", "player", "system.md"));
      if (text != null) {
        return text;
      }
    }
    return readIfExists(Paths.get(dir, "prompts", "system.md"));
  }

  private static String readIfExists(java.nio.file.Path path) throws IOException {
    return java.nio.file.Files.exists(path) ? java.nio.file.Files.readString(path).strip() : null;
  }

  private static int intSetting(Object value, int fallback) {
    return value == null ? fallback : Integer.parseInt(String.valueOf(value).trim());
  }

  @Override
  public String toString() {
    return "#<Boukensha::Config dir=" + dir + " tasks=...>";
  }

  private String resolveDir() {
    String env = System.getenv("BOUKENSHA_DIR");
    String raw = env != null ? env : DEFAULT_DIR;
    return Paths.get(raw).toAbsolutePath().toString();
  }

  /**
   * Ruby's Dotenv.load mutates ENV, so later ENV["KEY"] lookups just work. Java
   * cannot portably mutate the process environment, so we keep the loaded values
   * and expose them through env() instead.
   */
  private Dotenv loadEnv() {
    try {
      return Dotenv.configure()
          .directory(dir)
          .ignoreIfMissing()
          .load();
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Look up a variable from the config directory's .env, falling back to the
   * real process environment. Use this instead of System.getenv for anything
   * that may be supplied via .env.
   */
  public String env(String name) {
    if (dotenv != null) {
      String value = dotenv.get(name);
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    String value = System.getenv(name);
    return (value == null || value.isBlank()) ? null : value;
  }

  private Map<String, Object> loadSettings() {
    File settingsFile = new File(dir, "settings.yaml");
    if (settingsFile.exists()) {
      try (FileInputStream fis = new FileInputStream(settingsFile)) {
        Yaml yaml = new Yaml();
        Map<String, Object> loaded = yaml.load(fis);
        return loaded != null ? loaded : new HashMap<>();
      } catch (IOException e) {
        return new HashMap<>();
      }
    }
    return new HashMap<>();
  }
}
