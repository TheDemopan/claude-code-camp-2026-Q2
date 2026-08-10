package com.boukensha;

import com.boukensha.api.Client;
import com.boukensha.exception.ApiError;
import com.boukensha.model.Context;
import com.boukensha.model.Message;
import com.boukensha.model.PromptBuilder;
import com.boukensha.tool.Registry;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Agent {
  private static final int MAX_ITERATIONS = 25;
  private static final int WRAP_UP_OUTPUT_TOKENS = 400;
  private static final String WRAP_UP_DIRECTIVE =
      "You have reached your action limit for this turn. Do not call any more tools.\n" +
      "Briefly summarize what you accomplished, what is still unfinished, and the\n" +
      "single next action you would take.";

  private final Context context;
  private final Registry registry;
  private final PromptBuilder builder;
  private final Client client;
  private final int maxIterations;
  private final Integer maxOutputTokens;
  private int iteration;

  public Agent(Context context, Registry registry, PromptBuilder builder, Client client,
               Integer maxIterations, Integer maxOutputTokens) {
    this.context = context;
    this.registry = registry;
    this.builder = builder;
    this.client = client;
    this.maxIterations = maxIterations != null ? maxIterations : MAX_ITERATIONS;
    this.maxOutputTokens = maxOutputTokens;
    this.iteration = 0;
  }

  @SuppressWarnings("unchecked")
  public String run() {
    while (true) {
      if (iterationLimitReached()) {
        return wrapUp("max_iterations");
      }

      iteration++;
      System.out.println("[iteration " + iteration + "/" + maxIterations + "]");

      Map<String, Object> callOpts = maxOutputTokens != null
          ? Map.of("max_output_tokens", maxOutputTokens)
          : Map.of();

      Map<String, Object> response = client.call(callOpts);
      Map<String, Object> parsed = builder.parseResponse(response);

      String stopReason = (String) parsed.get("stop_reason");
      List<Object> content = (List<Object>) parsed.get("content");

      if ("tool_use".equals(stopReason)) {
        handleToolCalls(content);
      } else {
        return extractText(content);
      }
    }
  }

  private boolean iterationLimitReached() {
    return maxIterations > 0 && iteration >= maxIterations;
  }

  @SuppressWarnings("unchecked")
  private String wrapUp(String reason) {
    context.addMessage("user", WRAP_UP_DIRECTIVE);
    try {
      Map<String, Object> response = client.call(
          Map.of("tools", List.of(), "max_output_tokens", WRAP_UP_OUTPUT_TOKENS));
      Map<String, Object> parsed = builder.parseResponse(response);
      List<Object> content = (List<Object>) parsed.get("content");
      String text = extractText(content).strip();
      return text.isEmpty() ? fallbackMessage(reason) : text;
    } catch (ApiError e) {
      return fallbackMessage(reason);
    }
  }

  private String fallbackMessage(String reason) {
    return "I reached my " + maxIterations + "-action limit for this turn before finishing " +
           "(" + reason + "). Ask me to continue and I'll pick up from here.";
  }

  private String extractText(List<Object> content) {
    return content.stream()
        .filter(block -> block instanceof Map)
        .map(block -> (Map<String, Object>) block)
        .filter(block -> "text".equals(block.get("type")))
        .map(block -> (String) block.get("text"))
        .collect(Collectors.joining());
  }

  @SuppressWarnings("unchecked")
  private void handleToolCalls(List<Object> content) {
    context.addMessage("assistant", content);

    content.stream()
        .filter(block -> block instanceof Map)
        .map(block -> (Map<String, Object>) block)
        .filter(block -> "tool_use".equals(block.get("type")))
        .forEach(block -> {
          String name = (String) block.get("name");
          Map<String, Object> args = (Map<String, Object>) block.get("input");
          String useId = (String) block.get("id");

          System.out.println("  tool call → " + name + "(" + args + ")");
          Object result = registry.dispatch(name, args);
          System.out.println("  tool result → " + result.toString().substring(0, Math.min(60, result.toString().length())));

          context.addMessage("tool_result", result.toString(), useId);
        });
  }
}
