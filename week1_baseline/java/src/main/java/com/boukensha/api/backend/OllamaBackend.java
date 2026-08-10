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

public class OllamaBackend extends Backend {
  private final String host;
  private final ObjectMapper objectMapper = new ObjectMapper();

  private static final Map<String, Map<String, Object>> MODELS = new LinkedHashMap<>();
  static {
    MODELS.put("gemma4:e4b", Map.of("context_window", 128_000, "cost_per_million", Map.of("input", 0.0, "output", 0.0), "usage_unit", "tokens"));
  }

  public OllamaBackend(String host, String model) throws UnsupportedModelError {
    this.host = host != null ? host : "http://localhost:11434";
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
      if ("tool_result".equals(msg.getRole())) {
        Map<String, Object> map = new HashMap<>();
        map.put("role", "tool");
        map.put("tool_name", msg.getToolUseId());
        map.put("content", msg.getContent());
        result.add(map);
      } else if ("assistant".equals(msg.getRole())) {
        result.add(assistantMessage((List<Map<String, Object>>) msg.getContent()));
      } else {
        Map<String, Object> map = new HashMap<>();
        map.put("role", msg.getRole());
        map.put("content", msg.getContent());
        result.add(map);
      }
    }
    return result;
  }

  private Map<String, Object> assistantMessage(List<Map<String, Object>> content) {
    List<Map<String, Object>> textBlocks = new ArrayList<>();
    List<Map<String, Object>> toolBlocks = new ArrayList<>();

    for (Map<String, Object> block : content) {
      if ("text".equals(block.get("type"))) {
        textBlocks.add(block);
      } else if ("tool_use".equals(block.get("type"))) {
        toolBlocks.add(block);
      }
    }

    Map<String, Object> result = new HashMap<>();
    result.put("role", "assistant");
    result.put("content", textBlocks.stream().map(b -> b.get("text")).reduce("", (a, b) -> a + b.toString()));

    if (!toolBlocks.isEmpty()) {
      List<Map<String, Object>> toolCalls = new ArrayList<>();
      for (Map<String, Object> block : toolBlocks) {
        Map<String, Object> toolCall = new HashMap<>();
        toolCall.put("id", block.get("id"));
        toolCall.put("type", "function");
        Map<String, Object> function = new HashMap<>();
        function.put("name", block.get("name"));
        try {
          function.put("arguments", objectMapper.writeValueAsString(block.get("input")));
        } catch (Exception e) {
          function.put("arguments", "{}");
        }
        toolCall.put("function", function);
        toolCalls.add(toolCall);
      }
      result.put("tool_calls", toolCalls);
    }

    return result;
  }

  @Override
  public List<Map<String, Object>> toTools(Map<String, Tool> tools) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (Tool tool : tools.values()) {
      Map<String, Object> toolMap = new HashMap<>();
      toolMap.put("type", "function");
      Map<String, Object> function = new HashMap<>();
      function.put("name", tool.getName());
      function.put("description", tool.getDescription());
      Map<String, Object> parameters = new HashMap<>();
      parameters.put("type", "object");
      parameters.put("properties", tool.getParameters());
      parameters.put("required", new ArrayList<>(tool.getParameters().keySet()));
      function.put("parameters", parameters);
      toolMap.put("function", function);
      result.add(toolMap);
    }
    return result;
  }

  @Override
  public Map<String, Object> toPayload(Context context, Map<String, Object> opts) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("model", model);
    payload.put("stream", false);
    payload.put("messages", toMessages(context.getMessages()));

    List<Map<String, Object>> tools = getTools(opts);
    payload.put("tools", tools != null ? tools : toTools(context.getTools()));
    payload.put("think", false);

    return payload;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Map<String, Object> parseResponse(Map<String, Object> response) {
    Map<String, Object> message = (Map<String, Object>) response.get("message");
    if (message == null) {
      message = new HashMap<>();
    }

    List<Map<String, Object>> content = new ArrayList<>();

    String msgContent = (String) message.get("content");
    if (msgContent != null && !msgContent.isEmpty()) {
      Map<String, Object> textBlock = new HashMap<>();
      textBlock.put("type", "text");
      textBlock.put("text", msgContent);
      content.add(textBlock);
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
    boolean hasToolCalls = toolCalls != null && !toolCalls.isEmpty();
    if (hasToolCalls) {
      for (Map<String, Object> tc : toolCalls) {
        Map<String, Object> toolBlock = new HashMap<>();
        toolBlock.put("type", "tool_use");
        toolBlock.put("id", tc.get("id"));
        Map<String, Object> func = (Map<String, Object>) tc.get("function");
        toolBlock.put("name", func.get("name"));
        try {
          String argsStr = (String) func.get("arguments");
          toolBlock.put("input", objectMapper.readValue(argsStr, Map.class));
        } catch (Exception e) {
          toolBlock.put("input", new HashMap<>());
        }
        content.add(toolBlock);
      }
    }

    Map<String, Object> result = new HashMap<>();
    result.put("stop_reason", hasToolCalls ? "tool_use" : "end_turn");
    result.put("content", content);
    return result;
  }

  @Override
  public Map<String, String> getHeaders() {
    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Type", "application/json");
    return headers;
  }

  @Override
  public String getUrl() {
    return host + "/api/chat";
  }
}
