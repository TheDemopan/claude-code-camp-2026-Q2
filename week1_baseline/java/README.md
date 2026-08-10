# Boukensha Java Port

A Java implementation of the Boukensha LLM agent framework. Run autonomous agents with multiple LLM backends (Anthropic Claude, OpenAI GPT, Google Gemini, Ollama).

## Overview

Boukensha is a framework for building LLM agents that can:
- **Iterate** up to 25 times per turn, calling tools and processing responses
- **Support multiple backends:** Anthropic Claude, OpenAI GPT, Google Gemini, Ollama (local & cloud)
- **Log sessions** to JSONL format for replay and analysis
- **Manage context** with message history and token tracking
- **Handle retries** with exponential backoff on transient failures

This is a strict 1:1 port from Ruby, preserving all behavior and semantics.

## Prerequisites

- **Java 17+**
- **Maven 3.6+**
- **API Keys** (at least one):
  - `ANTHROPIC_API_KEY` for Claude
  - `OPENAI_API_KEY` for GPT
  - `GEMINI_API_KEY` for Gemini
  - Ollama (local) requires running `ollama serve` on `localhost:11434`
  - `OLLAMA_API_KEY` for Ollama Cloud

## Building

```bash
cd week1_baseline/java
mvn clean compile
```

**Dependencies:**
- Jackson (JSON handling)
- OkHttp (HTTP client with automatic retries)
- dotenv4j (environment variable loading)
- SnakeYAML (YAML config parsing)
- JUnit 5 (testing)

## Usage

### 1. Configuration

Create `~/.boukensha/settings.yaml`:

```yaml
mud:
  host: localhost
  port: 4000
  username: player_name
  password: secret

tasks:
  player:
    provider: anthropic
    model: claude-haiku-4-5-20251001
```

Create `~/.boukensha/.env`:

```bash
ANTHROPIC_API_KEY=sk-ant-...
OPENAI_API_KEY=sk-...
GEMINI_API_KEY=...
OLLAMA_API_KEY=...
```

### 2. Basic Agent Run

```java
import com.boukensha.*;
import com.boukensha.api.Client;
import com.boukensha.api.backend.*;
import com.boukensha.config.Config;
import com.boukensha.model.*;
import com.boukensha.tool.Registry;
import com.boukensha.logger.SessionLogger;

public class Example {
  public static void main(String[] args) throws Exception {
    // Load configuration
    Config config = new Config();
    
    // Set up context
    String system = "You are a helpful assistant.";
    Context context = new Context(system);
    
    // Register tools
    Registry registry = new Registry(context);
    registry.tool("echo", "Echo back the input", 
      Map.of("text", Map.of("type", "string")),
      args -> "Echo: " + args.get("text"));
    
    // Create backend (uses API key from config)
    Backend backend = new AnthropicBackend(
      System.getenv("ANTHROPIC_API_KEY"),
      "claude-haiku-4-5-20251001"
    );
    
    // Wire up agent
    PromptBuilder builder = new PromptBuilder(context, backend);
    Client client = new Client(builder);
    SessionLogger logger = new SessionLogger(
      null, config.getDir(), null,
      Map.of("model", "claude-haiku-4-5-20251001", "provider", "anthropic")
    );
    
    Agent agent = new Agent(context, registry, builder, client, 25, null);
    
    // Add user task
    context.addMessage("user", "What is 2+2?");
    
    // Run agent
    String result = agent.run();
    System.out.println(result);
    
    logger.close();
  }
}
```

### 3. Switching Backends

```java
// Anthropic
Backend backend = new AnthropicBackend(apiKey, "claude-haiku-4-5-20251001");

// OpenAI
Backend backend = new OpenAIBackend(apiKey, "gpt-5.4");

// Google Gemini
Backend backend = new GeminiBackend(apiKey, "gemini-2.5-flash");

// Ollama (local)
Backend backend = new OllamaBackend("http://localhost:11434", "gemma4:e4b");

// Ollama Cloud
Backend backend = new OllamaCloudBackend(apiKey, "gemma4:31b-cloud");
```

### 4. Supported Models

**Anthropic:**
- `claude-haiku-4-5`
- `claude-haiku-4-5-20251001`
- `claude-sonnet-4-6`
- `claude-opus-4-8`

**OpenAI:**
- `gpt-5.5`
- `gpt-5.4`
- `gpt-5.4-mini`

**Gemini:**
- `gemini-3.5-flash`
- `gemini-3.1-flash-lite`
- `gemini-2.5-pro`
- `gemini-2.5-flash`
- `gemini-2.5-flash-lite`

**Ollama (local):**
- `gemma4:e4b`

**Ollama Cloud:**
- `gemma4:31b-cloud`
- `kimi-k2.5:cloud`
- `minimax-m3:cloud`

## Core Classes

### `Agent`
Main agent loop. Iterates up to 25 times:
- Calls backend to get response
- Checks `stop_reason` (tool_use or end_turn)
- If tool_use: dispatch tools and add results to context
- Repeats until end_turn or iteration limit
- On limit: calls wrap-up to summarize gracefully

**Key methods:**
- `run()` → String (final response)

### `Client`
HTTP client with automatic retry logic.
- MAX_RETRIES: 3
- BASE_DELAY: 0.5 seconds
- Backoff: 0.5 * 2^(attempt-1) seconds
- Retryable status codes: 408, 409, 429, 500, 502, 503, 504

**Key methods:**
- `call(Map opts)` → Map (parsed JSON response)

### `Context`
Manages conversation state.
- Stores messages, tools, system prompt
- Tracks token usage
- Supports message compaction when context window fills

**Key methods:**
- `addMessage(String role, Object content, String toolUseId)`
- `registerTool(Tool tool)`
- `getMessages()`, `getTools()`
- `compactMessages(double targetFraction)`

### `Backend` (interface)
Each backend handles:
- Message transformation (normalize to common shape)
- Tool definition wrapping (backend-specific format)
- Response parsing (normalize to common shape)
- Headers and URL construction

**Key methods:**
- `toMessages(List messages)` → backend-specific format
- `toTools(Map tools)` → backend-specific format
- `toPayload(Context, opts)` → HTTP request body
- `parseResponse(Map)` → normalized {stop_reason, content}
- `getHeaders()`, `getUrl()`

### `SessionLogger`
Logs all events to JSONL.
- One JSON object per line
- Fields: session_id, at (ISO8601 timestamp), phase, phase-specific data
- Phases: session_start, iteration, prompt, tool_call, tool_result, response, limit_reached, turn_end

**Key methods:**
- `iteration(n, max)`
- `prompt(messages, tools)`
- `toolCall(name, args)`
- `toolResult(name, result, ok, error)`
- `response(text, usage, stopReason, task, backend)`
- `close()`

### `Tool`
Represents a callable tool.
- Name, description, parameters
- Block is a `Function<Map, Object>` lambda

**Key methods:**
- `invoke(Map args)` → Object (result)

### `Registry`
Manages tool registration and dispatch.

**Key methods:**
- `tool(name, description, parameters, block)` → Tool
- `dispatch(name, args)` → Object (calls tool block)

## Logging & Session Replay

Each run generates a `.jsonl` file in `~/.boukensha/sessions/`:

```json
{"session_id":"20260810T120000Z-abc123","at":"2026-08-10T12:00:00Z","phase":"session_start","model":"claude-haiku-4-5-20251001","provider":"anthropic"}
{"session_id":"20260810T120000Z-abc123","at":"2026-08-10T12:00:01Z","phase":"iteration","n":1,"max":25}
{"session_id":"20260810T120000Z-abc123","at":"2026-08-10T12:00:02Z","phase":"prompt","message_count":2,"messages":[...]}
{"session_id":"20260810T120000Z-abc123","at":"2026-08-10T12:00:05Z","phase":"response","text":"The answer is 4.","stop_reason":"end_turn"}
```

Use these logs to:
- Replay agent decisions
- Analyze tool usage patterns
- Track token consumption
- Debug failures

## Error Handling

**Custom exceptions:**
- `ApiError` — HTTP/network failures
- `UnknownToolError` — tool not registered
- `UnsupportedModelError` — invalid model for backend
- `BoukenkshaException` — base exception

```java
try {
  agent.run();
} catch (ApiError e) {
  System.err.println("API call failed: " + e.getMessage());
} catch (UnknownToolError e) {
  System.err.println("Tool not found: " + e.getMessage());
}
```

## Message Format

All messages are normalized to a common shape internally:

```java
// Content for user/assistant messages
List<Map<String, Object>> content = List.of(
  Map.of("type", "text", "text", "Hello")
);

// Content for tool calls
content = List.of(
  Map.of("type", "tool_use", "id", "call_123", "name", "echo", "input", Map.of("text", "hi"))
);

// Tool results
context.addMessage("tool_result", "Echo: hi", "call_123");
```

Each backend transforms these to its native format (Anthropic content blocks, OpenAI tool_calls, Gemini parts, etc.).

## Retry Behavior

The `Client` automatically retries on:
- **Transient errors:** Socket timeouts, connection resets, SSL errors
- **Retryable status codes:** 408, 409, 429, 500, 502, 503, 504

**Backoff schedule:**
- Attempt 1: 0.5 seconds
- Attempt 2: 1.0 seconds
- Attempt 3: 2.0 seconds
- Fail after 3 attempts

Each backend maintains its own HTTP client, so retries are transparent to your code.

## Project Structure

```
week1_baseline/java/
├── pom.xml                          # Maven build config
├── .gitignore                       # Git ignore rules
└── src/main/java/com/boukensha/
    ├── Agent.java                   # Main agent loop
    ├── api/
    │   ├── Client.java              # HTTP client with retry logic
    │   └── backend/
    │       ├── Backend.java         # Base interface
    │       ├── AnthropicBackend.java
    │       ├── OpenAIBackend.java
    │       ├── GeminiBackend.java
    │       ├── OllamaBackend.java
    │       └── OllamaCloudBackend.java
    ├── config/
    │   └── Config.java              # ~/.boukensha resolution + YAML/env loading
    ├── exception/                   # Custom exceptions
    │   ├── BoukenkshaException.java
    │   ├── ApiError.java
    │   ├── UnknownToolError.java
    │   └── UnsupportedModelError.java
    ├── logger/
    │   └── SessionLogger.java       # JSONL logging
    ├── model/
    │   ├── Message.java             # Role + content + toolUseId
    │   ├── Context.java             # State management
    │   └── PromptBuilder.java       # Backend adapter
    └── tool/
        ├── Tool.java                # Tool definition
        └── Registry.java            # Tool registration & dispatch
```

## Testing

Unit tests not yet ported (Ruby had no existing tests). Integration tests should:
- Test each backend against real APIs (use sandbox credentials)
- Verify JSONL schema matches Ruby output exactly
- Test retry logic by simulating transient errors
- Validate message transformations per backend

## Future Work (Phases D-E)

- **RunBuilder (DSL):** Fluent API for registering tools inline
- **CLI Launcher:** `java -jar boukensha.jar --backend anthropic --model claude-haiku-4-5-20251001 "your task"`
- **REPL Loop:** Interactive console with `/exit`, `/clear`, `/help` commands
- **Context Compaction:** Automatic message pruning when token limit approaches
- **Web UI (log_viz):** HTTP server to replay and visualize session logs

## Contributing

This is a 1:1 port from Ruby. Key principles:
- Match Ruby behavior exactly (no "improvements")
- Use Java idioms where they don't change semantics
- Keep message format cross-backend compatible
- Log to exact JSONL schema for compatibility

## License

[Same as Ruby implementation]

## References

- Ruby original: `week1_baseline/ruby/`
- Plan: `docs/plans/java_port/00_config.md`
- Session logs: `~/.boukensha/sessions/*.jsonl`
