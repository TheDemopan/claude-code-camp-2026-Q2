package com.boukensha.api.backend;

import com.boukensha.exception.UnsupportedModelError;
import com.boukensha.model.Context;
import com.boukensha.model.Message;
import com.boukensha.tool.Tool;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GeminiBackend extends Backend {
  private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models";
  private final String apiKey;
  private final ObjectMapper objectMapper = new ObjectMapper();

  private static final Map<String, Map<String, Object>> MODELS = new LinkedHashMap<>();
  static {
    MODELS.put("gemini-3.5-flash", Map.of("context_window", 1_048_576, "cost_per_million", Map.of("input", 1.5, "output", 9.0), "usage_unit", "tokens"));
    MODELS.put("gemini-3.1-flash-lite", Map.of("context_window", 1_048_576, "cost_per_million", Map.of("input", 0.25, "output", 1.5), "usage_unit", "tokens"));
    MODELS.put("gemini-2.5-pro", Map.of("context_window", 1_048_576, "cost_per_million", Map.of("input", 1.25, "output", 10.0), "usage_unit", "tokens"));
    MODELS.put("gemini-2.5-flash", Map.of("context_window", 1_048_576, "cost_per_million", Map.of("input", 0.30, "output", 2.50), "usage_unit", "tokens"));
    MODELS.put("gemini-2.5-flash-lite", Map.of("context_window", 1_048_576, "cost_per_million", Map.of("input", 0.10, "output", 0.40), "usage_unit", "tokens"));
  }

  public GeminiBackend(String apiKey, String model) throws UnsupportedModelError {
    this.apiKey = apiKey;
    validateModel(model);
  }

  @Override
  public Map<String, Map<String, Object>> getModels() {
    return MODELS;
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<Map<String, Object>> toMessages(List<Message> messages) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (Message msg : messages) {
      if ("assistant".equals(msg.getRole())) {
        Map<String, Object> map = new HashMap<>();
        map.put("role", "model");
        map.put("parts", assistantParts((List<Map<String, Object>>) msg.getContent()));
        result.add(map);
      } else if ("tool_result".equals(msg.getRole())) {
        Map<String, Object> map = new HashMap<>();
        map.put("role", "user");
        List<Map<String, Object>> parts = new ArrayList<>();
        Map<String, Object> funcResponse = new HashMap<>();
        funcResponse.put("functionResponse", Map.of(
            "name", msg.getToolUseId(),
            "response", Map.of("content", msg.getContent())
        ));
        parts.add(funcResponse);
        map.put("parts", parts);
        result.add(map);
      } else {
        Map<String, Object> map = new HashMap<>();
        map.put("role", msg.getRole());
        List<Map<String, Object>> parts = new ArrayList<>();
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("text", msg.getContent());
        parts.add(textPart);
        map.put("parts", parts);
        result.add(map);
      }
    }
    return result;
  }

  private List<Map<String, Object>> assistantParts(List<Map<String, Object>> content) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (Map<String, Object> block : content) {
      if ("tool_use".equals(block.get("type"))) {
        Map<String, Object> part = new HashMap<>();
        Map<String, Object> funcCall = new HashMap<>();
        funcCall.put("name", block.get("name"));
        funcCall.put("args", block.get("input"));
        part.put("functionCall", funcCall);
        result.add(part);
      } else if ("text".equals(block.get("type"))) {
        Map<String, Object> part = new HashMap<>();
        part.put("text", block.get("text"));
        result.add(part);
      }
    }
    return result;
  }

  @Override
  public List<Map<String, Object>> toTools(Map<String, Tool> tools) {
    if (tools.isEmpty()) {
      return new ArrayList<>();
    }

    List<Map<String, Object>> declarations = new ArrayList<>();
    for (Tool tool : tools.values()) {
      Map<String, Object> decl = new HashMap<>();
      decl.put("name", tool.getName());
      decl.put("description", tool.getDescription());
      Map<String, Object> parameters = new HashMap<>();
      parameters.put("type", "object");
      parameters.put("properties", tool.getParameters());
      parameters.put("required", new ArrayList<>(tool.getParameters().keySet()));
      decl.put("parameters", parameters);
      declarations.add(decl);
    }

    List<Map<String, Object>> result = new ArrayList<>();
    Map<String, Object> toolSpec = new HashMap<>();
    toolSpec.put("functionDeclarations", declarations);
    result.add(toolSpec);
    return result;
  }

  @Override
  public Map<String, Object> toPayload(Context context, Map<String, Object> opts) {
    Map<String, Object> payload = new HashMap<>();

    Map<String, Object> systemInstruction = new HashMap<>();
    List<Map<String, Object>> sysParts = new ArrayList<>();
    Map<String, Object> sysPart = new HashMap<>();
    sysPart.put("text", context.getSystem());
    sysParts.add(sysPart);
    systemInstruction.put("parts", sysParts);
    payload.put("systemInstruction", systemInstruction);

    payload.put("contents", toMessages(context.getMessages()));

    List<Map<String, Object>> tools = getTools(opts);
    payload.put("tools", tools != null ? tools : toTools(context.getTools()));

    Map<String, Object> generationConfig = new HashMap<>();
    generationConfig.put("maxOutputTokens", getMaxOutputTokens(opts));
    payload.put("generationConfig", generationConfig);

    return payload;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Map<String, Object> parseResponse(Map<String, Object> response) {
    List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
    List<Map<String, Object>> parts = new ArrayList<>();
    boolean toolUsed = false;

    if (candidates != null && !candidates.isEmpty()) {
      Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
      if (content != null) {
        parts = (List<Map<String, Object>>) content.get("parts");
      }
    }

    List<Map<String, Object>> content = new ArrayList<>();
    if (parts != null) {
      for (Map<String, Object> part : parts) {
        if (part.containsKey("functionCall")) {
          Map<String, Object> fc = (Map<String, Object>) part.get("functionCall");
          String funcName = (String) fc.get("name");
          Map<String, Object> args = (Map<String, Object>) fc.get("args");

          Map<String, Object> toolUseBlock = new HashMap<>();
          toolUseBlock.put("type", "tool_use");
          toolUseBlock.put("id", funcName);
          toolUseBlock.put("name", funcName);
          toolUseBlock.put("input", args != null ? args : new HashMap<>());
          content.add(toolUseBlock);
          toolUsed = true;
        } else if (part.containsKey("text")) {
          Map<String, Object> textBlock = new HashMap<>();
          textBlock.put("type", "text");
          textBlock.put("text", part.get("text"));
          content.add(textBlock);
        }
      }
    }

    Map<String, Object> result = new HashMap<>();
    result.put("stop_reason", toolUsed ? "tool_use" : "end_turn");
    result.put("content", content);
    return result;
  }

  @Override
  public Map<String, String> getHeaders() {
    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Type", "application/json");
    headers.put("x-goog-api-key", apiKey);
    return headers;
  }

  @Override
  public String getUrl() {
    return BASE_URL + "/" + model + ":generateContent";
  }
}
