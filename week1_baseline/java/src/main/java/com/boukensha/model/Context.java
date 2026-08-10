package com.boukensha.model;

import com.boukensha.tool.Tool;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Context {
  private final String system;
  private final List<Message> messages = new ArrayList<>();
  private final Map<String, Tool> tools = new LinkedHashMap<>();
  private final int contextWindow;
  private final String workingDir;
  private final double compactionThreshold;
  private int currentTokens;
  private int turnTokens;

  public Context(String system) {
    this(system, 200_000, null, 0.85);
  }

  public Context(String system, int contextWindow, String workingDir, double compactionThreshold) {
    this.system = system;
    this.contextWindow = contextWindow;
    this.workingDir = workingDir == null ? null : Paths.get(workingDir).toAbsolutePath().toString();
    this.compactionThreshold = compactionThreshold;
  }

  public String getSystem() {
    return system;
  }

  public List<Message> getMessages() {
    return messages;
  }

  public Map<String, Tool> getTools() {
    return tools;
  }

  public int getContextWindow() {
    return contextWindow;
  }

  public String getWorkingDir() {
    return workingDir;
  }

  public double getCompactionThreshold() {
    return compactionThreshold;
  }

  public int getCurrentTokens() {
    return currentTokens;
  }

  public void setCurrentTokens(int tokens) {
    this.currentTokens = tokens;
  }

  public int getTurnTokens() {
    return turnTokens;
  }

  public void registerTool(Tool tool) {
    tools.put(tool.getName(), tool);
  }

  public void addMessage(String role, Object content) {
    addMessage(role, content, null);
  }

  public void addMessage(String role, Object content, String toolUseId) {
    messages.add(new Message(role, content, toolUseId));
  }

  /** Update the known context size from the last response's input_tokens. */
  public void updateTokens(int n) {
    this.currentTokens = n;
  }

  /** Reset the cumulative per-turn spend counter, called at the top of a turn. */
  public void resetTurnTokens() {
    this.turnTokens = 0;
  }

  /**
   * Add one call's input+output to the per-turn total. This is the spend budget,
   * distinct from currentTokens which tracks window pressure.
   */
  public void addTurnTokens(int input, int output) {
    this.turnTokens += input + output;
  }

  public double getUsageFraction() {
    return contextWindow > 0 ? (double) currentTokens / contextWindow : 0.0;
  }

  public int getUsagePct() {
    return (int) Math.round(getUsageFraction() * 100);
  }

  public boolean needsCompaction() {
    return needsCompaction(compactionThreshold);
  }

  public boolean needsCompaction(double threshold) {
    return getUsageFraction() >= threshold;
  }

  public int compactMessages() {
    return compactMessages(0.60);
  }

  /**
   * Drop the oldest 40% of messages, keeping at least 2. Resets currentTokens to
   * 0; the next API response supplies the real figure. Returns the drop count.
   */
  public int compactMessages(double targetFraction) {
    int dropCount = (int) Math.ceil(messages.size() * (1.0 - targetFraction));
    dropCount = Math.min(dropCount, messages.size() - 2);
    dropCount = Math.max(dropCount, 0);

    if (dropCount > 0) {
      messages.subList(0, dropCount).clear();
    }
    currentTokens = 0;
    return dropCount;
  }

  /** Drop all history, keeping tools and the system prompt intact. */
  public void clearMessages() {
    messages.clear();
    currentTokens = 0;
  }

  public int getToolCount() {
    return tools.size();
  }

  public int getTurnCount() {
    return messages.size();
  }

  @Override
  public String toString() {
    return "#<Context turns=" + getTurnCount() + " tools=" + getToolCount()
        + " window=" + contextWindow + " current=" + currentTokens + ">";
  }
}
