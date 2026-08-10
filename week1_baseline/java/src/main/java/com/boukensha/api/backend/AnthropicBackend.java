package com.boukensha.api.backend;

import com.boukensha.exception.UnsupportedModelError;
import com.boukensha.model.Context;
import com.boukensha.model.Message;
import com.boukensha.tool.Tool;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AnthropicBackend extends Backend {
  private static final String BASE_URL = "https://api.anthropic.com/v1/messages";
  private final String apiKey;

  private static final Map<String, Map<String, Object>> MODELS = new LinkedHashMap<>();
  static {
    MODELS.put("claude-haiku-4-5", Map.of("context_window", 200_000, "cost_per_million", Map.of("input", 1.0, "output", 5.0), "usage_unit", "tokens"));
    MODELS.put("claude-haiku-4-5-20251001", Map.of("context_window", 200_000, "cost_per_million", Map.of("input", 1.0, "output", 5.0), "usage_unit", "tokens"));
    MODELS.put("claude-sonnet-4-6", Map.of("context_window", 1_000_000, "cost_per_million", Map.of("input", 3.0, "output", 15.0), "usage_unit", "tokens"));
    MODELS.put("claude-opus-4-8", Map.of("context_window", 1_000_000, "cost_per_million", Map.of("input", 5.0, "output", 25.0), "usage_unit", "tokens"));
  }

  public AnthropicBackend(String apiKey, String model) throws UnsupportedModelError {
    this.apiKey = apiKey;
    validateModel(model);
  }

  @Override
  public Map<String, Map<String, Object>> getModels() {
    return MODELS;
  }

  @Override
  public List<Map<String, Object>> toMessages(List<Message> messages) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (Message msg : messages) {
      if ("tool_result".equals(msg.getRole())) {
        Map<String, Object> map = new HashMap<>();
        map.put("role", "user");
        List<Map<String, Object>> content = new ArrayList<>();
        Map<String, Object> toolResult = new HashMap<>();
        toolResult.put("type", "tool_result");
        toolResult.put("tool_use_id", msg.getToolUseId());
        toolResult.put("content", msg.getContent());
        content.add(toolResult);
        map.put("content", content);
        result.add(map);
      } else {
        Map<String, Object> map = new HashMap<>();
        map.put("role", msg.getRole());
        map.put("content", msg.getContent());
        result.add(map);
      }
    }
    return result;
  }

  @Override
  public List<Map<String, Object>> toTools(Map<String, Tool> tools) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (Tool tool : tools.values()) {
      Map<String, Object> toolMap = new HashMap<>();
      toolMap.put("name", tool.getName());
      toolMap.put("description", tool.getDescription());
      Map<String, Object> inputSchema = new HashMap<>();
      inputSchema.put("type", "object");
      inputSchema.put("properties", tool.getParameters());
      inputSchema.put("required", new ArrayList<>(tool.getParameters().keySet()));
      toolMap.put("input_schema", inputSchema);
      result.add(toolMap);
    }
    return result;
  }

  @Override
  public Map<String, Object> toPayload(Context context, Map<String, Object> opts) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("model", model);
    payload.put("system", context.getSystem());
    payload.put("max_tokens", getMaxOutputTokens(opts));

    List<Map<String, Object>> tools = getTools(opts);
    payload.put("tools", tools != null ? tools : toTools(context.getTools()));
    payload.put("messages", toMessages(context.getMessages()));

    return payload;
  }

  @Override
  public Map<String, Object> parseResponse(Map<String, Object> response) {
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> contentList = (List<Map<String, Object>>) response.get("content");
    String stopReason = "tool_use".equals(response.get("stop_reason")) ? "tool_use" : "end_turn";

    Map<String, Object> result = new HashMap<>();
    result.put("stop_reason", stopReason);
    result.put("content", contentList != null ? contentList : new ArrayList<>());
    return result;
  }

  @Override
  public Map<String, String> getHeaders() {
    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Type", "application/json");
    headers.put("x-api-key", apiKey);
    headers.put("anthropic-version", "2023-06-01");
    return headers;
  }

  @Override
  public String getUrl() {
    return BASE_URL;
  }
}
