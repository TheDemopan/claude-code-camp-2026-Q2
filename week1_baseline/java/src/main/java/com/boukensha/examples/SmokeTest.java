package com.boukensha.examples;

import com.boukensha.Agent;
import com.boukensha.api.Client;
import com.boukensha.api.backend.AnthropicBackend;
import com.boukensha.api.backend.Backend;
import com.boukensha.logger.SessionLogger;
import com.boukensha.model.Context;
import com.boukensha.model.PromptBuilder;
import com.boukensha.tool.Registry;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Exercises the full agent loop against a local stub that speaks the Anthropic
 * response shape. No credentials, no external traffic, no cost.
 */
public class SmokeTest {

  private static final String TOOL_USE = """
      {"stop_reason":"tool_use",
       "usage":{"input_tokens":100,"output_tokens":20},
       "content":[
         {"type":"text","text":"I should echo that."},
         {"type":"tool_use","id":"toolu_1","name":"echo","input":{"text":"hello"}}
       ]}
      """;

  private static final String END_TURN = """
      {"stop_reason":"end_turn",
       "usage":{"input_tokens":150,"output_tokens":30},
       "content":[{"type":"text","text":"The tool said: Echo: hello"}]}
      """;

  private static int failures = 0;

  public static void main(String[] args) throws Exception {
    AtomicInteger callCount = new AtomicInteger();
    AtomicInteger toolInvocations = new AtomicInteger();

    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      // Drain the request so the client sees a clean response.
      exchange.getRequestBody().readAllBytes();
      String body = callCount.incrementAndGet() == 1 ? TOOL_USE : END_TURN;
      byte[] out = body.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, out.length);
      exchange.getResponseBody().write(out);
      exchange.close();
    });
    server.start();
    int port = server.getAddress().getPort();

    Backend backend = new AnthropicBackend("stub-key", "claude-haiku-4-5") {
      @Override
      public String getUrl() {
        return "http://127.0.0.1:" + port + "/v1/messages";
      }
    };

    Path logPath = Files.createTempFile("smoke", ".jsonl");
    Context context = new Context("You are a test agent.");
    Registry registry = new Registry(context);
    registry.tool("echo", "Echo the input",
        Map.of("text", Map.of("type", "string")),
        toolArgs -> {
          toolInvocations.incrementAndGet();
          return "Echo: " + toolArgs.get("text");
        });

    PromptBuilder builder = new PromptBuilder(context, backend);
    Client client = new Client(builder);
    SessionLogger logger = new SessionLogger(null, null, logPath.toString(), Map.of("model", "claude-haiku-4-5"));
    Agent agent = new Agent(context, registry, builder, client, logger, 25, 0, 1024);

    context.addMessage("user", "Please echo hello.");
    String result = agent.run();
    logger.close();
    server.stop(0);

    System.out.println("--- result ---");
    System.out.println(result);
    System.out.println();

    check("two API calls made", 2, callCount.get());
    check("tool invoked once", 1, toolInvocations.get());
    check("final text returned", "The tool said: Echo: hello", result);
    check("turn tokens accumulated (100+20+150+30)", 300, context.getTurnTokens());
    check("current tokens from last input_tokens", 150, context.getCurrentTokens());

    // user, assistant(tool_use), tool_result, assistant(final)
    check("message count", 4, context.getMessages().size());
    check("tool_result recorded", "Echo: hello", context.getMessages().get(2).getContent());
    check("tool_use_id round-tripped", "toolu_1", context.getMessages().get(2).getToolUseId());
    check("final assistant message stored", "The tool said: Echo: hello",
        context.getMessages().get(3).getContent());

    List<String> log = Files.readAllLines(logPath);
    System.out.println();
    System.out.println("--- " + log.size() + " log lines ---");
    log.forEach(System.out::println);

    checkTrue("log has session_start", log.stream().anyMatch(l -> l.contains("\"session_start\"")));
    checkTrue("log has iteration", log.stream().anyMatch(l -> l.contains("\"iteration\"")));
    checkTrue("log has plan (preamble)", log.stream().anyMatch(l -> l.contains("\"plan\"")));
    checkTrue("log has tool_call", log.stream().anyMatch(l -> l.contains("\"tool_call\"")));
    checkTrue("log has tool_result ok", log.stream().anyMatch(l -> l.contains("\"tool_result\"") && l.contains("\"ok\":true")));
    checkTrue("log has turn_end completed", log.stream().anyMatch(l -> l.contains("\"turn_end\"") && l.contains("completed")));
    checkTrue("every line has session_id", log.stream().allMatch(l -> l.contains("\"session_id\"")));
    checkTrue("every line has timestamp", log.stream().allMatch(l -> l.contains("\"at\"")));

    System.out.println();
    if (failures == 0) {
      System.out.println("ALL CHECKS PASSED");
    } else {
      System.out.println(failures + " CHECK(S) FAILED");
      System.exit(1);
    }
  }

  private static void check(String label, Object expected, Object actual) {
    boolean ok = expected.equals(actual);
    if (!ok) {
      failures++;
    }
    System.out.println((ok ? "  PASS  " : "  FAIL  ") + label
        + (ok ? "" : "  (expected " + expected + ", got " + actual + ")"));
  }

  private static void checkTrue(String label, boolean ok) {
    if (!ok) {
      failures++;
    }
    System.out.println((ok ? "  PASS  " : "  FAIL  ") + label);
  }
}
