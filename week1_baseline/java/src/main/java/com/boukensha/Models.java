package com.boukensha;

import java.util.Map;

/**
 * Static model to capability table.
 *
 * context_window is a known model fact, not something the user configures.
 * Unknown models fall back to a conservative default so an unrecognised id
 * cannot silently assume a huge window.
 */
public final class Models {
  private static final Map<String, Integer> CONTEXT_WINDOWS = Map.of(
      "claude-opus-4-8", 200_000,
      "claude-sonnet-4-6", 200_000,
      "claude-haiku-4-5", 200_000);

  public static final int DEFAULT_CONTEXT_WINDOW = 32_000;

  private Models() {
  }

  public static int contextWindow(String model) {
    if (model == null) {
      return DEFAULT_CONTEXT_WINDOW;
    }
    return CONTEXT_WINDOWS.getOrDefault(model, DEFAULT_CONTEXT_WINDOW);
  }
}
