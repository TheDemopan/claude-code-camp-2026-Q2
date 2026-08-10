package com.boukensha.logger;

import com.boukensha.model.Message;
import com.boukensha.tool.Tool;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * JSONL session log. Every line carries session_id and an ISO-8601 timestamp
 * alongside the phase-specific fields, matching the Ruby logger byte for byte
 * in field names so existing log tooling keeps working.
 */
public class SessionLogger implements AutoCloseable {
  private static final String DEFAULT_SESSION_DIR = "sessions";
  private static final DateTimeFormatter SESSION_ID_FORMAT =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

  private final String sessionId;
  private final String path;
  private final Writer logWriter;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final List<Consumer<Map<String, Object>>> subscribers = new ArrayList<>();
  private boolean debug;

  public SessionLogger(String dir) throws IOException {
    this(null, dir, null, Map.of());
  }

  public SessionLogger(String sessionId, String dir, String logPath, Map<String, Object> snapshot)
      throws IOException {
    this.sessionId = sessionId != null ? sessionId : generateSessionId();
    this.path = logPath != null ? logPath : defaultPath(dir);

    String parent = new File(this.path).getParent();
    if (parent != null) {
      Files.createDirectories(Paths.get(parent));
    }
    this.logWriter = Files.newBufferedWriter(Paths.get(this.path), StandardCharsets.UTF_8,
        StandardOpenOption.CREATE, StandardOpenOption.APPEND);

    Map<String, Object> start = new LinkedHashMap<>();
    start.put("phase", "session_start");
    start.putAll(snapshot);
    write(start);
  }

  public String getSessionId() {
    return sessionId;
  }

  public String getPath() {
    return path;
  }

  /** Mirrors Boukensha.debug? gating on the raw phase. */
  public void setDebug(boolean debug) {
    this.debug = debug;
  }

  /** Streams every event to a listener, as Logger#subscribe does for the TUI. */
  public void subscribe(Consumer<Map<String, Object>> subscriber) {
    subscribers.add(subscriber);
  }

  public void turn(int n) {
    write(event("turn", "n", n));
  }

  public void iteration(int n, int max) {
    write(event("iteration", "n", n, "max", max));
  }

  public void limitReached(String kind, int n, int max) {
    write(event("limit_reached", "kind", kind, "n", n, "max", max));
  }

  public void turnEnd(String reason, int iterations, Integer tokens) {
    write(event("turn_end", "reason", reason, "iterations", iterations, "tokens", tokens));
  }

  public void prompt(List<Message> messages, Map<String, Tool> tools, int contextWindow) {
    List<Map<String, Object>> serialized = new ArrayList<>();
    for (Message m : messages) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("role", m.getRole());
      entry.put("content", m.getContent());
      serialized.add(entry);
    }
    write(event("prompt",
        "message_count", messages.size(),
        "messages", serialized,
        "tool_count", tools.size(),
        "tools", new ArrayList<>(tools.keySet()),
        "context_window", contextWindow));
  }

  public void compaction(int before, int dropped, int contextWindow) {
    write(event("compaction", "before", before, "dropped", dropped, "context_window", contextWindow));
  }

  public void toolCall(String name, Object args) {
    write(event("tool_call", "name", name, "args", args));
  }

  public void toolResult(String name, Object result, boolean ok, String error) {
    write(event("tool_result", "name", name, "result", String.valueOf(result), "ok", ok, "error", error));
  }

  public void response(String text, Object usage, String stopReason) {
    write(event("response", "text", text == null ? "" : text.strip(), "usage", usage, "stop_reason", stopReason));
  }

  public void reasoning(String text, boolean redacted) {
    write(event("reasoning", "text", text == null ? "" : text, "redacted", redacted));
  }

  public void plan(String text) {
    write(event("plan", "text", text == null ? "" : text.strip()));
  }

  public void raw(Object data) {
    if (!debug) {
      return;
    }
    write(event("raw", "data", data));
  }

  @Override
  public void close() {
    try {
      logWriter.close();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Builds an ordered event map. Null values are kept, since Ruby emits explicit
   * nulls for absent usage/error/tokens and log consumers rely on the key.
   */
  private Map<String, Object> event(String phase, Object... keyValues) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("phase", phase);
    for (int i = 0; i + 1 < keyValues.length; i += 2) {
      map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
    }
    return map;
  }

  private void write(Map<String, Object> event) {
    Map<String, Object> full = new LinkedHashMap<>(event);
    full.put("session_id", sessionId);
    full.put("at", Instant.now().toString());

    try {
      logWriter.write(objectMapper.writeValueAsString(full));
      logWriter.write("\n");
      logWriter.flush();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    for (Consumer<Map<String, Object>> subscriber : subscribers) {
      subscriber.accept(event);
    }
  }

  private String defaultPath(String dir) {
    String base = dir != null ? dir : DEFAULT_SESSION_DIR;
    return Paths.get(base, sessionId + ".jsonl").toString();
  }

  private String generateSessionId() {
    String timestamp = SESSION_ID_FORMAT.format(Instant.now());
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    return timestamp + "-" + suffix;
  }

  /** No-op sink for callers that want an agent without a log file. */
  public static SessionLogger nullLogger() {
    try {
      return new SessionLogger(null, null, File.createTempFile("boukensha-null", ".jsonl").getAbsolutePath(),
          new HashMap<>());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
