package com.boukensha.tui;

import com.boukensha.logger.SessionLogger;
import com.boukensha.model.Context;
import com.boukensha.repl.Repl;
import java.io.IOException;
import java.util.Map;

/**
 * Live terminal view of the agent working.
 *
 * NOTE: this is a reimplementation, not a port. The Ruby TUI is built on charm
 * (bubbletea + lipgloss + bubbles) — an Elm-architecture TUI with a viewport and
 * progress widgets. Java has no equivalent library, so rather than mimic that
 * architecture this renders the same information with plain ANSI, driven by the
 * logger's event stream. Behaviour therefore differs from the Ruby original by
 * design; the four-zone layout and key handling are not reproduced.
 */
public class Tui {
  // ANSI
  private static final String RESET = "[0m";
  private static final String DIM = "[2m";
  private static final String BOLD = "[1m";
  private static final String CYAN = "[36m";
  private static final String GREEN = "[32m";
  private static final String YELLOW = "[33m";
  private static final String RED = "[31m";
  private static final String GREY = "[90m";

  private final Repl repl;
  private final Context context;

  public Tui(Repl repl, Context context) {
    this.repl = repl;
    this.context = context;
  }

  /** Subscribe to the logger's event stream and start the REPL loop. */
  public void start(SessionLogger logger) throws IOException {
    logger.subscribe(this::renderEvent);
    repl.onOutput(this::renderReplOutput);
    repl.start();
  }

  /** One line per logged phase, so the user watches the turn unfold live. */
  private void renderEvent(Map<String, Object> event) {
    String phase = String.valueOf(event.get("phase"));
    switch (phase) {
      case "iteration":
        System.out.println(GREY + "  · iteration " + event.get("n") + "/" + event.get("max")
            + statusSuffix() + RESET);
        break;
      case "plan":
        System.out.println(CYAN + "  · " + truncate(String.valueOf(event.get("text")), 100) + RESET);
        break;
      case "tool_call":
        System.out.println(YELLOW + "  → " + event.get("name") + " " + event.get("args") + RESET);
        break;
      case "tool_result":
        boolean ok = Boolean.TRUE.equals(event.get("ok"));
        System.out.println((ok ? GREEN : RED) + "  ← "
            + truncate(String.valueOf(event.get("result")), 100) + RESET);
        break;
      case "compaction":
        System.out.println(YELLOW + "  ⚑ compacted: dropped " + event.get("dropped")
            + " messages" + RESET);
        break;
      case "limit_reached":
        System.out.println(RED + "  ⚑ limit reached: " + event.get("kind")
            + " (" + event.get("n") + "/" + event.get("max") + ")" + RESET);
        break;
      case "turn_end":
        System.out.println(DIM + "  · turn end (" + event.get("reason") + ", "
            + event.get("iterations") + " iterations, " + event.get("tokens") + " tokens)" + RESET);
        break;
      default:
        // session_start, prompt, response, reasoning, raw: not surfaced live
        break;
    }
  }

  private void renderReplOutput(String text) {
    System.out.println(text);
    if (text != null && !text.isBlank() && !text.startsWith("\n╔")) {
      System.out.print(BOLD + Repl.PROMPT + RESET);
      System.out.flush();
    }
  }

  /** Context pressure, coloured the way the Ruby status bar colours it. */
  private String statusSuffix() {
    int pct = context.getUsagePct();
    String colour = pct >= 85 ? RED : pct >= 60 ? YELLOW : GREEN;
    return DIM + "  [ctx " + colour + pct + "%" + DIM + " · "
        + context.getTurnTokens() + " tok]" + RESET;
  }

  private static String truncate(String text, int max) {
    if (text == null) {
      return "";
    }
    String flat = text.replace("\n", " ").strip();
    return flat.length() <= max ? flat : flat.substring(0, max) + "…";
  }
}
