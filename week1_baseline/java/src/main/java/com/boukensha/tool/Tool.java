package com.boukensha.tool;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public class Tool {
  private final String name;
  private final String description;
  private final Map<String, Object> parameters;
  private final Function<Map<String, Object>, Object> block;

  public Tool(String name, String description, Map<String, Object> parameters, Function<Map<String, Object>, Object> block) {
    this.name = Objects.requireNonNull(name, "name cannot be null");
    this.description = Objects.requireNonNull(description, "description cannot be null");
    this.parameters = Objects.requireNonNull(parameters, "parameters cannot be null");
    this.block = Objects.requireNonNull(block, "block cannot be null");
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public Map<String, Object> getParameters() {
    return parameters;
  }

  public Function<Map<String, Object>, Object> getBlock() {
    return block;
  }

  public Object invoke(Map<String, Object> args) {
    return block.apply(args);
  }

  @Override
  public String toString() {
    String paramStr = String.join(", ", parameters.keySet());
    String descShort = description.length() > 40 ? description.substring(0, 40) : description;
    return String.format("#<Tool name=%s description=%s params=[%s]>", name, descShort, paramStr);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Tool tool = (Tool) o;
    return Objects.equals(name, tool.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name);
  }
}
