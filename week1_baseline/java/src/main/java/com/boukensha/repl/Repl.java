package com.boukensha.repl;

import com.boukensha.Agent;
import com.boukensha.api.Client;
import com.boukensha.exception.ApiError;
import com.boukensha.exception.BoukenshaException;
import com.boukensha.logger.SessionLogger;
import com.boukensha.model.Context;
import com.boukensha.model.PromptBuilder;
import com.boukensha.tool.Registry;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The interactive session loop. It wraps the same primitives as a single
 * Boukensha.run call, but stays alive: read a task, run the agent, print the
 * reply, loop.
 *
 * The Context is shared across turns, so conversation history accumulates and
 * the agent sees the full transcript each time.
 */
public class Repl {
  public static final String PROMPT = "boukensha> ";

  private static final String HELP = """
      Commands:
        /clear    wipe conversation history (tools stay)
        /compact  drop oldest 40% of messages to free context
        /exit     leave the REPL
        /help     show this message
      """;

  /** Result of handling a slash command. */
  public enum Command { QUIT, HANDLED, NOT_A_COMMAND }

  private final Context context;
  private final Registry registry;
  private final PromptBuilder builder;
  private final Client client;
  private final SessionLogger logger;
  private final String configDir;
  private final String provider;
  private final String model;
  private final String version;
  private final String apiKey;
  private final Map<String, Object> mud;
  private final Integer maxIterations;
  private final Integer maxTurnTokens;
  private final Integer maxOutputTokens;

  private int turn;
  private Consumer<String> outputCallback;

  public Repl(Context context, Registry registry, PromptBuilder builder, Client client,
              SessionLogger logger, String configDir, String provider, String model,
              String version, String apiKey, Map<String, Object> mud,
              Integer maxIterations, Integer maxTurnTokens, Integer maxOutputTokens) {
    this.context = context;
    this.registry = registry;
    this.builder = builder;
    this.client = client;
    this.logger = logger;
    this.configDir = configDir;
    this.provider = provider;
    this.model = model;
    this.version = version;
    this.apiKey = apiKey;
    this.mud = mud;
    this.maxIterations = maxIterations;
    this.maxTurnTokens = maxTurnTokens;
    this.maxOutputTokens = maxOutputTokens;
  }

  public Context getContext() {
    return context;
  }

  public SessionLogger getLogger() {
    return logger;
  }

  /**
   * Route every string the REPL would print through a callback instead. When
   * set, stdout printing is suppressed entirely. Used by the TUI.
   */
  public void onOutput(Consumer<String> callback) {
    this.outputCallback = callback;
  }

  public String banner() {
    String keyStatus = (apiKey == null || apiKey.isBlank()) ? "✗ API key not set" : "✓ API key set";
    String providerLine = (provider == null ? "default" : provider)
        + " (" + (model == null ? "default" : model) + ")  " + keyStatus;

    boolean configExists = configDir != null && Files.isDirectory(Paths.get(configDir));
    String configLine = configExists ? configDir
        : (configDir == null ? "(default)" : configDir) + "  ✗ directory not found";

    String ver = version == null ? "?.?.?" : version;
    String pad = " ".repeat(Math.max(0, 9 - ver.length()));

    return "\n"
        + "╔══════════════════════════════════════╗\n"
        + "║  BOUKENSHA MUD Assistant (v" + ver + ")" + pad + "║\n"
        + "╚══════════════════════════════════════╝\n"
        + "  config:    " + configLine + "\n"
        + "  provider:  " + providerLine + "\n"
        + "  mud:       " + mudStatusString() + "\n"
        + "\n"
        + "  /clear           reset conversation history\n"
        + "  /compact         free context (drop oldest messages)\n"
        + "  /exit or /quit    leave the REPL\n";
  }

  /** Handle a slash command. Returns NOT_A_COMMAND when the input is a task. */
  public Command handleCommand(String input) {
    switch (input) {
      case "/exit":
      case "/quit":
        output("Goodbye.");
        return Command.QUIT;
      case "/help":
        output(HELP);
        return Command.HANDLED;
      case "/clear":
        context.clearMessages();
        turn = 0;
        output("(conversation history cleared)");
        return Command.HANDLED;
      case "/compact":
        int dropped = context.compactMessages();
        output("(compacted context — " + dropped + " messages dropped)");
        return Command.HANDLED;
      default:
        return Command.NOT_A_COMMAND;
    }
  }

  public void runTurn(String input) {
    turn++;
    logger.turn(turn);
    context.addMessage("user", input);

    try {
      Agent agent = new Agent(context, registry, builder, client, logger,
          maxIterations, maxTurnTokens, maxOutputTokens);
      String result = agent.run();
      output("");
      output(result);
    } catch (ApiError e) {
      output("\n[error] API call failed: " + e.getMessage());
    } catch (BoukenshaException e) {
      output("\n[error] " + e.getMessage());
    }
  }

  public void start() throws IOException {
    output(banner());
    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

    while (true) {
      if (outputCallback == null) {
        System.out.print(PROMPT);
        System.out.flush();
      }

      String line = reader.readLine();
      if (line == null) {
        break; // EOF / Ctrl-D
      }

      String input = line.strip();
      if (input.isEmpty()) {
        continue;
      }

      Command result = handleCommand(input);
      if (result == Command.QUIT) {
        break;
      }
      if (result == Command.HANDLED) {
        continue;
      }

      runTurn(input);
    }
  }

  private void output(String text) {
    if (outputCallback != null) {
      outputCallback.accept(text == null ? "" : text);
    } else {
      System.out.println(text);
    }
  }

  /**
   * Banner MUD status. TCP reachability only — the tool session auto-connects at
   * startup, so probing login here would cause a double-login.
   */
  private String mudStatusString() {
    if (mud == null) {
      return "(not configured)";
    }
    String host = mud.get("host") == null ? "localhost" : String.valueOf(mud.get("host"));
    int port = mud.get("port") == null ? 4000 : Integer.parseInt(String.valueOf(mud.get("port")));
    Object name = mud.get("name");

    return host + ":" + port + "  " + probeMud(host, port, name);
  }

  private String probeMud(String host, int port, Object name) {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(host, port), 3000);
    } catch (Exception e) {
      return "✗ not reachable";
    }
    boolean hasName = name != null && !String.valueOf(name).isBlank();
    return hasName ? "(Reachable)" : "(Reachable, no credentials)";
  }
}
