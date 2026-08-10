package com.boukensha.model;

import java.util.Objects;

public class Message {
  private final String role;
  private final Object content;
  private final String toolUseId;

  public Message(String role, Object content) {
    this(role, content, null);
  }

  public Message(String role, Object content, String toolUseId) {
    this.role = Objects.requireNonNull(role, "role cannot be null");
    this.content = Objects.requireNonNull(content, "content cannot be null");
    this.toolUseId = toolUseId;
  }

  public String getRole() {
    return role;
  }

  public Object getContent() {
    return content;
  }

  public String getToolUseId() {
    return toolUseId;
  }

  public boolean isToolResult() {
    return "tool_result".equals(role);
  }

  @Override
  public String toString() {
    String idTag = toolUseId != null ? " [" + toolUseId + "]" : "";
    String contentStr = content instanceof String ? (String) content :
                        content != null ? content.toString().substring(0, Math.min(60, content.toString().length())) : "";
    return String.format("#<Message role=%s%s content=%s...>", role, idTag, contentStr);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Message message = (Message) o;
    return Objects.equals(role, message.role) &&
           Objects.equals(content, message.content) &&
           Objects.equals(toolUseId, message.toolUseId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(role, content, toolUseId);
  }
}
