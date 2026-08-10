package com.boukensha;

import com.boukensha.api.Client;
import com.boukensha.exception.ApiError;
import com.boukensha.logger.SessionLogger;
import com.boukensha.model.Context;
import com.boukensha.model.PromptBuilder;
import com.boukensha.tool.Registry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Agent {
  /**
   * Default iteration ceiling. The enforced value comes from the constructor
   * argument; 0 or null disables the ceiling.
   */
  public static final int MAX_ITERATIONS = 25;

  /** The wind-down call is deliberately short and cheap. */
  private static final int WRAP_UP_OUTPUT_TOKENS = 400;
  private static final String WRAP_UP_DIRECTIVE =
      "You have reached your action limit for this turn. Do not call any more tools.\n"
      + "Briefly summarize what you accomplished, what is still unfinished, and the\n"
      + "single next action you would take.";

  private final Context context;
  private final Registry registry;
  private final PromptBuilder builder;
  private final Client client;
  private final SessionLogger logger;
  private final int maxIterations;
  private final int maxTurnTokens;
  private final Integer maxOutputTokens;
  private int iteration;

  public Agent(Context context, Registry registry, PromptBuilder builder, Client client,
               SessionLogger logger, Integer maxIterations, Integer maxTurnTokens, Integer maxOutputTokens) {
    this.context = context;
    this.registry = registry;
    this.builder = builder;
    this.client = client;
    this.logger = logger;
    this.maxIterations = maxIterations == null ? MAX_ITERATIONS : maxIterations;
    this.maxTurnTokens = maxTurnTokens == null ? 0 : maxTurnTokens;
    this.maxOutputTokens = maxOutputTokens;
  }

  public String run() {
    context.resetTurnTokens();
    compactIfNeeded();

    while (true) {
      // Two independent ceilings; stop at whichever trips first. These are
      // trigger thresholds, not hard caps: on reaching one we stop starting new
      // iterations and make exactly one terminal wind-down call.
      if (iterationLimitReached()) {
        logger.limitReached("max_iterations", iteration, maxIterations);
        return wrapUp("max_iterations");
      }
      if (tokenLimitReached()) {
        logger.limitReached("max_tokens", context.getTurnTokens(), maxTurnTokens);
        return wrapUp("max_tokens");
      }

      iteration++;
      logger.iteration(iteration, maxIterations);
      logger.prompt(context.getMessages(), context.getTools(), context.getContextWindow());

      Map<String, Object> response = client.call(callOpts());
      logger.raw(response);
      Map<String, Object> parsed = builder.parseResponse(response);
      recordUsage(response);

      List<Map<String, Object>> content = contentBlocks(parsed);
      logReasoning(content);

      if ("tool_use".equals(parsed.get("stop_reason"))) {
        handleToolCalls(content, response);
      } else {
        String text = extractText(content);
        logger.response(text, response.get("usage"), (String) parsed.get("stop_reason"));
        logger.turnEnd("completed", iteration, context.getTurnTokens());
        context.addMessage("assistant", text);
        return text;
      }
    }
  }

  private boolean iterationLimitReached() {
    return maxIterations > 0 && iteration >= maxIterations;
  }

  private boolean tokenLimitReached() {
    return maxTurnTokens > 0 && context.getTurnTokens() >= maxTurnTokens;
  }

  private Map<String, Object> callOpts() {
    Map<String, Object> opts = new LinkedHashMap<>();
    if (maxOutputTokens != null) {
      opts.put("max_output_tokens", maxOutputTokens);
    }
    return opts;
  }

  /**
   * Add this call's input+output to the turn total (spend budget) and refresh the
   * known context size from input_tokens (compaction pressure).
   */
  @SuppressWarnings("unchecked")
  private void recordUsage(Map<String, Object> response) {
    Object rawUsage = response.get("usage");
    Map<String, Object> usage = rawUsage instanceof Map ? (Map<String, Object>) rawUsage : Map.of();
    int input = intValue(usage.get("input_tokens"));
    int output = intValue(usage.get("output_tokens"));
    context.addTurnTokens(input, output);
    context.updateTokens(input);
  }

  private static int intValue(Object value) {
    return value instanceof Number ? ((Number) value).intValue() : 0;
  }

  private void compactIfNeeded() {
    if (!context.needsCompaction()) {
      return;
    }
    int before = context.getCurrentTokens();
    int dropped = context.compactMessages();
    logger.compaction(before, dropped, context.getContextWindow());
  }

  /**
   * One final, tools-disabled call so the turn ends in character rather than
   * aborting. Runs outside the counted loop: it never re-checks the limits and
   * does not increment the iteration counter, though its tokens still count
   * toward the reported total. Falls back to a fixed message if the call fails.
   */
  private String wrapUp(String reason) {
    context.addMessage("user", WRAP_UP_DIRECTIVE);
    try {
      Map<String, Object> response =
          client.call(Map.of("tools", List.of(), "max_output_tokens", WRAP_UP_OUTPUT_TOKENS));
      Map<String, Object> parsed = builder.parseResponse(response);
      String text = extractText(contentBlocks(parsed));
      if (text.strip().isEmpty()) {
        text = fallbackMessage(reason);
      }
      recordUsage(response);
      logger.response(text, response.get("usage"), (String) parsed.get("stop_reason"));
      logger.turnEnd(reason, iteration, context.getTurnTokens());
      context.addMessage("assistant", text);
      return text;
    } catch (ApiError e) {
      String msg = fallbackMessage(reason);
      logger.turnEnd(reason, iteration, context.getTurnTokens());
      context.addMessage("assistant", msg);
      return msg;
    }
  }

  private String fallbackMessage(String reason) {
    return "I reached my " + maxIterations + "-action limit for this turn before finishing "
        + "(" + reason + "). Ask me to continue and I'll pick up from here.";
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> contentBlocks(Map<String, Object> parsed) {
    Object content = parsed.get("content");
    if (!(content instanceof List)) {
      return List.of();
    }
    List<Map<String, Object>> blocks = new ArrayList<>();
    for (Object block : (List<Object>) content) {
      if (block instanceof Map) {
        blocks.add((Map<String, Object>) block);
      }
    }
    return blocks;
  }

  private String extractText(List<Map<String, Object>> content) {
    return content.stream()
        .filter(b -> "text".equals(b.get("type")))
        .map(b -> String.valueOf(b.get("text")))
        .collect(Collectors.joining("\n"));
  }

  /**
   * One reasoning event per reasoning block so a viewer can show the model's
   * thinking as a first-class step. Empty non-redacted blocks are skipped; a
   * redacted block still renders, since it tells the viewer the model thought here.
   */
  private void logReasoning(List<Map<String, Object>> content) {
    for (Map<String, Object> block : content) {
      if (!"reasoning".equals(block.get("type"))) {
        continue;
      }
      boolean redacted = Boolean.TRUE.equals(block.get("redacted"));
      String text = block.get("text") == null ? "" : String.valueOf(block.get("text"));
      if (text.strip().isEmpty() && !redacted) {
        continue;
      }
      logger.reasoning(text, redacted);
    }
  }

  @SuppressWarnings("unchecked")
  private void handleToolCalls(List<Map<String, Object>> content, Map<String, Object> response) {
    List<Map<String, Object>> toolCalls = content.stream()
        .filter(b -> "tool_use".equals(b.get("type")))
        .collect(Collectors.toList());

    // Log any preamble text that came with the tool call, then the placeholder
    // that owns this turn's usage chip.
    String preamble = extractText(content);
    if (!preamble.strip().isEmpty()) {
      logger.plan(preamble);
    }
    logger.response("(tool use — " + toolCalls.size() + " call" + (toolCalls.size() == 1 ? "" : "s") + ")",
        response.get("usage"), "tool_use");

    context.addMessage("assistant", content);

    for (Map<String, Object> block : toolCalls) {
      String name = (String) block.get("name");
      Object args = block.get("input");
      String useId = (String) block.get("id");

      logger.toolCall(name, args);

      Object result;
      try {
        Map<String, Object> toolArgs = args instanceof Map ? (Map<String, Object>) args : Map.of();
        result = registry.dispatch(name, toolArgs);
        logger.toolResult(name, result, true, null);
      } catch (RuntimeException e) {
        // A failing tool must not kill the turn; hand the error back to the model.
        result = "ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage();
        logger.toolResult(name, result, false, e.getMessage());
      }

      context.addMessage("tool_result", String.valueOf(result), useId);
    }
  }
}
