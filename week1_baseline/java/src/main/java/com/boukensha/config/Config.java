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
  private final Map<String, Object> settings;

  public Config() {
    this.dir = resolveDir();
    loadEnv();
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

  @Override
  public String toString() {
    return "#<Boukensha::Config dir=" + dir + " tasks=...>";
  }

  private String resolveDir() {
    String env = System.getenv("BOUKENSHA_DIR");
    String raw = env != null ? env : DEFAULT_DIR;
    return Paths.get(raw).toAbsolutePath().toString();
  }

  private void loadEnv() {
    File envFile = new File(dir, ".env");
    if (envFile.exists()) {
      try {
        Dotenv.configure()
            .directory(dir)
            .ignoreIfMissing()
            .load();
      } catch (Exception e) {
        // Silently fail if dotenv loading fails
      }
    }
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
