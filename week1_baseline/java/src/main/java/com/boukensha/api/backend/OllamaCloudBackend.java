package com.boukensha.api.backend;

import com.boukensha.exception.UnsupportedModelError;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class OllamaCloudBackend extends OllamaBackend {
  private static final String BASE_URL = "https://ollama.com/api/chat";
  private final String apiKey;

  private static final Map<String, Map<String, Object>> MODELS = new LinkedHashMap<>();
  static {
    MODELS.put("gemma4:31b-cloud", Map.of("context_window", 256_000, "cost_per_million", Map.of("input", 0.0, "output", 0.0), "usage_unit", "tokens"));
    MODELS.put("kimi-k2.5:cloud", Map.of("context_window", 1_048_576, "cost_per_million", Map.of("input", 0.0, "output", 0.0), "usage_unit", "tokens"));
    MODELS.put("minimax-m3:cloud", Map.of("context_window", 1_000_000, "cost_per_million", Map.of("input", 0.0, "output", 0.0), "usage_unit", "tokens"));
  }

  public OllamaCloudBackend(String apiKey, String model) throws UnsupportedModelError {
    super("https://ollama.com", model);
    this.apiKey = apiKey;
    validateModel(model);
  }

  @Override
  public Map<String, Map<String, Object>> getModels() {
    return MODELS;
  }

  @Override
  public Map<String, String> getHeaders() {
    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Type", "application/json");
    headers.put("Authorization", "Bearer " + apiKey);
    return headers;
  }

  @Override
  public String getUrl() {
    return BASE_URL;
  }
}
