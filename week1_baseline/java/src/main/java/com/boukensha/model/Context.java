package com.boukensha.model;

import com.boukensha.tool.Tool;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Context {
  private final String system;
  private final List<Message> messages;
  private final Map<String, Tool> tools;
  private final int contextWindow;
  private int currentTokens;
  private int turnTokens;
  private final double compactionThreshold;

  public Context(String system) {
    this(system, 200_000, null, 0.85);
  }

  public Context(String system, int contextWindow, String workingDir, double compactionThreshold) {
    this.system = system;
    this.messages = new ArrayList<>();
    this.tools = new HashMap<>();
    this.contextWindow = contextWindow;
    this.currentTokens = 0;
    this.turnTokens = 0;
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

  public int getCurrentTokens() {
    return currentTokens;
  }

  public void setCurrentTokens(int tokens) {
    this.currentTokens = tokens;
  }

  public int getTurnTokens() {
    return turnTokens;
  }

  public void resetTurnTokens() {
    this.turnTokens = 0;
  }

  public void addTurnTokens(int input, int output) {
    this.turnTokens += input + output;
  }

  public void registerTool(Tool tool) {
    tools.put(tool.getName(), tool);
  }

  public void addMessage(String role, Object content) {
    addMessage(role, content, null);
  }

  public void addMessage(String role, Object content, String toolUseId) {
    Message msg = new Message(role, content, toolUseId);
    messages.add(msg);
  }

  public double getUsageFraction() {
    return (double) currentTokens / contextWindow;
  }

  public int getUsagePct() {
    return (int) Math.round(getUsageFraction() * 100);
  }

  public boolean needsCompaction() {
    return getUsageFraction() >= compactionThreshold;
  }

  public int compactMessages(double targetFraction) {
    if (messages.size() < 2) {
      return 0;
    }
    int dropCount = (int) Math.ceil(messages.size() * (1 - targetFraction));
    dropCount = Math.min(dropCount, messages.size() - 2);
    for (int i = 0; i < dropCount; i++) {
      messages.remove(0);
    }
    currentTokens = (int) (currentTokens * targetFraction);
    return dropCount;
  }

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
    return "#<Context messages=" + messages.size() + " tools=" + tools.size() +
           " usage=" + getUsagePct() + "%>";
  }
}
