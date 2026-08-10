package com.boukensha.dsl;

import com.boukensha.tool.Registry;
import com.boukensha.tool.Tool;
import java.util.Map;
import java.util.function.Function;

/**
 * The object a Boukensha.run block is evaluated against. It exposes only tool,
 * keeping the DSL surface intentionally small.
 *
 * Ruby uses instance_eval so the block sees `tool` as a bare method; Java has no
 * equivalent, so callers receive the RunDSL instance as a lambda parameter.
 */
public class RunDSL {
  private final Registry registry;

  public RunDSL(Registry registry) {
    this.registry = registry;
  }

  public Tool tool(String name, String description, Map<String, Object> parameters,
                   Function<Map<String, Object>, Object> block) {
    return registry.tool(name, description, parameters, block);
  }

  /**
   * The underlying registry, so a tool library (FileSystemTools, ShellTools,
   * MudTools) can register its whole set at once. Ruby reaches these through
   * Boukensha.run's working_dir:/mud: keywords instead.
   */
  public Registry registry() {
    return registry;
  }

  /** The block form passed to Boukensha.run / Boukensha.repl. */
  @FunctionalInterface
  public interface Block {
    void define(RunDSL dsl);
  }
}
