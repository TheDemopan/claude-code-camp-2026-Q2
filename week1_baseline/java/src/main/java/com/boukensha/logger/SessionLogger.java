package com.boukensha.logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SessionLogger {
  private static final String DEFAULT_SESSION_DIR = "sessions";

  private final String sessionId;
  private final String path;
  private final FileWriter logWriter;
  private final ObjectMapper objectMapper;

  public SessionLogger() throws IOException {
    this(null, null, null, new HashMap<>());
  }

  public SessionLogger(String sessionId, String dir, String logPath, Map<String, Object> snapshot) throws IOException {
    this.sessionId = sessionId != null ? sessionId : generateSessionId();
    this.path = logPath != null ? logPath : buildPath(dir);
    this.objectMapper = new ObjectMapper();

    // Ensure directory exists
    Files.createDirectories(Paths.get(new java.io.File(path).getParent()));

    this.logWriter = new FileWriter(path, true);
    writeLog(mergeMap(snapshot, Map.of("phase", "session_start")));
  }

  public String getSessionId() {
    return sessionId;
  }

  public String getPath() {
    return path;
  }

  public void iteration(int n, int max) throws IOException {
    writeLog(Map.of("phase", "iteration", "n", n, "max", max));
  }

  public void limitReached(String kind, int n, int max) throws IOException {
    writeLog(Map.of("phase", "limit_reached", "kind", kind, "n", n, "max", max));
  }

  public void turnEnd(String reason, int iterations, Integer tokens) throws IOException {
    Map<String, Object> event = new HashMap<>();
    event.put("phase", "turn_end");
    event.put("reason", reason);
    event.put("iterations", iterations);
    if (tokens != null) {
      event.put("tokens", tokens);
    }
    writeLog(event);
  }

  public void prompt(List<Map<String, Object>> messages, Map<String, Object> tools) throws IOException {
    Map<String, Object> event = new HashMap<>();
    event.put("phase", "prompt");
    event.put("message_count", messages.size());
    event.put("messages", messages);
    event.put("tool_count", tools.size());
    event.put("tools", new ArrayList<>(tools.keySet()));
    writeLog(event);
  }

  public void toolCall(String name, Map<String, Object> args) throws IOException {
    writeLog(Map.of("phase", "tool_call", "name", name, "args", args));
  }

  public void toolResult(String name, String result, boolean ok, String error) throws IOException {
    Map<String, Object> event = new HashMap<>();
    event.put("phase", "tool_result");
    event.put("name", name);
    event.put("result", result);
    event.put("ok", ok);
    if (error != null) {
      event.put("error", error);
    }
    writeLog(event);
  }

  public void response(String text, Map<String, Object> usage, String stopReason, String task, String backend) throws IOException {
    Map<String, Object> event = new HashMap<>();
    event.put("phase", "response");
    event.put("text", text != null ? text.strip() : "");
    if (usage != null) {
      event.put("usage", usage);
    }
    if (stopReason != null) {
      event.put("stop_reason", stopReason);
    }
    if (task != null) {
      event.put("task", task);
    }
    if (backend != null) {
      event.put("provider", backend);
    }
    writeLog(event);
  }

  public void close() throws IOException {
    if (logWriter != null) {
      logWriter.close();
    }
  }

  private void writeLog(Map<String, Object> event) throws IOException {
    Map<String, Object> fullEvent = new HashMap<>(event);
    fullEvent.put("session_id", sessionId);
    fullEvent.put("at", Instant.now().toString());

    String json = objectMapper.writeValueAsString(fullEvent);
    logWriter.write(json + "\n");
    logWriter.flush();
  }

  private String buildPath(String dir) {
    String sessionDir = dir != null ? dir : DEFAULT_SESSION_DIR;
    return sessionDir + "/" + sessionId + ".jsonl";
  }

  private String generateSessionId() {
    String timestamp = java.time.format.DateTimeFormatter
        .ofPattern("yyyyMMdd'T'HHmmss'Z'")
        .withZone(java.time.ZoneId.of("UTC"))
        .format(Instant.now());
    String uuid = UUID.randomUUID().toString().substring(0, 8);
    return timestamp + "-" + uuid;
  }

  private Map<String, Object> mergeMap(Map<String, Object> base, Map<String, Object> override) {
    Map<String, Object> result = new HashMap<>(base);
    result.putAll(override);
    return result;
  }
}
