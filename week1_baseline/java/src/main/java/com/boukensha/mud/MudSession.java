package com.boukensha.mud;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Long-lived telnet connection to a CircleMUD server.
 *
 * A background thread continuously drains the socket into an internal buffer,
 * stripping telnet IAC negotiation bytes. Callers send a command and then call
 * readUntilPrompt (or readUntilQuiet) to collect both the command's response and
 * any async chatter that arrived in the meantime.
 */
public class MudSession implements AutoCloseable {
  public static final String DEFAULT_HOST = "localhost";
  public static final int DEFAULT_PORT = 4000;
  public static final double DEFAULT_TIMEOUT_SECONDS = 10.0;

  /**
   * CircleMUD terminates every command response with a prompt ending in "> ".
   * Waiting for that sentinel is faster and more deterministic than a silence
   * window — it returns as soon as the server signals it is done.
   */
  public static final String PROMPT_SENTINEL = "> ";

  // Telnet bytes we recognise. We never negotiate; we consume and discard.
  private static final int IAC = 0xFF;
  private static final int DONT = 0xFE;
  private static final int DO = 0xFD;
  private static final int WONT = 0xFC;
  private static final int WILL = 0xFB;
  private static final int SB = 0xFA;
  private static final int SE = 0xF0;

  public static class MudException extends RuntimeException {
    public MudException(String message) {
      super(message);
    }
  }

  public static class ConnectionError extends MudException {
    public ConnectionError(String message) {
      super(message);
    }
  }

  public static class LoginError extends MudException {
    public LoginError(String message) {
      super(message);
    }
  }

  public static class TimeoutError extends MudException {
    public TimeoutError(String message) {
      super(message);
    }
  }

  private final String host;
  private final int port;
  private final double timeoutSeconds;

  private final ReentrantLock lock = new ReentrantLock();
  private final Condition bufferChanged = lock.newCondition();
  private final StringBuilder buffer = new StringBuilder();

  private Socket socket;
  private Thread reader;
  private volatile boolean closed;
  private Double lastReceivedAt;

  public MudSession(String host, int port) {
    this(host, port, DEFAULT_TIMEOUT_SECONDS);
  }

  public MudSession(String host, int port, double timeoutSeconds) {
    this.host = host == null ? DEFAULT_HOST : host;
    this.port = port <= 0 ? DEFAULT_PORT : port;
    this.timeoutSeconds = timeoutSeconds;
  }

  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }

  public boolean isOpen() {
    return socket != null && !closed;
  }

  public MudSession open() {
    if (socket != null) {
      throw new MudException("already open");
    }
    try {
      Socket s = new Socket();
      s.connect(new InetSocketAddress(host, port), (int) (timeoutSeconds * 1000));
      this.socket = s;
      this.closed = false;
      startReader();
      return this;
    } catch (IOException e) {
      throw new ConnectionError("connect " + host + ":" + port + " failed: " + e.getMessage());
    }
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    try {
      if (socket != null) {
        socket.close();
      }
    } catch (IOException e) {
      // already closed / broken — fine
    }
    if (reader != null) {
      try {
        reader.join(1000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    socket = null;
    reader = null;
  }

  /** Send a command line. A trailing CRLF is appended. Returns the line sent. */
  public String sendCommand(String command) {
    if (!isOpen()) {
      throw new MudException("session not open");
    }
    String line = command == null ? "" : command;
    try {
      OutputStream out = socket.getOutputStream();
      out.write((line + "\r\n").getBytes(StandardCharsets.UTF_8));
      out.flush();
    } catch (IOException e) {
      throw new ConnectionError("write failed: " + e.getMessage());
    }
    return line;
  }

  /** Send a bare newline — the Ruby :return / :enter form. */
  public String sendReturn() {
    return sendCommand("");
  }

  /** Drain whatever is currently buffered and return it. Non-blocking. */
  public String drain() {
    lock.lock();
    try {
      String out = buffer.toString();
      buffer.setLength(0);
      return out;
    } finally {
      lock.unlock();
    }
  }

  /**
   * Block until quietSeconds elapse with no new bytes, or the timeout passes.
   * Returns whatever accumulated.
   */
  public String readUntilQuiet(double quietSeconds, Double timeout) {
    if (!isOpen()) {
      throw new MudException("session not open");
    }
    double deadline = monotime() + (timeout == null ? timeoutSeconds : timeout);
    lock.lock();
    try {
      while (true) {
        double remainingTotal = deadline - monotime();
        if (remainingTotal <= 0) {
          break;
        }
        if (lastReceivedAt != null && (monotime() - lastReceivedAt) >= quietSeconds
            && buffer.length() > 0) {
          break;
        }
        double waitFor = (lastReceivedAt != null && buffer.length() > 0)
            ? quietSeconds - (monotime() - lastReceivedAt)
            : remainingTotal;
        waitFor = Math.min(waitFor, remainingTotal);
        if (waitFor <= 0) {
          break;
        }
        awaitQuietly(waitFor);
      }
      String out = buffer.toString();
      buffer.setLength(0);
      return out;
    } finally {
      lock.unlock();
    }
  }

  public String readUntilQuiet() {
    return readUntilQuiet(1.0, null);
  }

  /**
   * Block until the buffer matches the pattern, then return everything up to and
   * including the match, leaving the remainder buffered.
   */
  public String readUntil(Pattern pattern, Double timeout) {
    if (!isOpen()) {
      throw new MudException("session not open");
    }
    double deadline = monotime() + (timeout == null ? timeoutSeconds : timeout);
    lock.lock();
    try {
      while (true) {
        Matcher matcher = pattern.matcher(buffer);
        if (matcher.find()) {
          int cut = matcher.end();
          String out = buffer.substring(0, cut);
          buffer.delete(0, cut);
          return out;
        }
        double remaining = deadline - monotime();
        if (remaining <= 0) {
          throw new TimeoutError("readUntil " + pattern.pattern() + " timed out");
        }
        if (closed) {
          throw new ConnectionError("socket closed while waiting");
        }
        awaitQuietly(remaining);
      }
    } finally {
      lock.unlock();
    }
  }

  public String readUntil(String literal, Double timeout) {
    return readUntil(Pattern.compile(Pattern.quote(literal)), timeout);
  }

  /**
   * Wait for CircleMUD's "> " prompt sentinel. Falls back to draining the buffer
   * if the prompt never arrives (e.g. during combat, when async lines slip in).
   */
  public String readUntilPrompt(Double timeout) {
    try {
      return readUntil(PROMPT_SENTINEL, timeout);
    } catch (TimeoutError e) {
      System.err.println("[MudSession] prompt not detected within timeout; returning buffered content");
      return drain();
    }
  }

  public String readUntilPrompt() {
    return readUntilPrompt(null);
  }

  /** Walk the CircleMUD login dance. */
  public String login(String username, String password) {
    readUntil(Pattern.compile("By what name do you wish to be known.*\\?",
        Pattern.CASE_INSENSITIVE), null);
    sendCommand(username);

    readUntil(Pattern.compile("Password", Pattern.CASE_INSENSITIVE), null);
    sendCommand(password);

    String output = readUntil(
        Pattern.compile("Welcome|Reconnecting|Wrong password", Pattern.CASE_INSENSITIVE), null);

    if (Pattern.compile("Wrong password", Pattern.CASE_INSENSITIVE).matcher(output).find()) {
      throw new LoginError("wrong password");
    }
    if (Pattern.compile("Reconnecting", Pattern.CASE_INSENSITIVE).matcher(output).find()) {
      return output; // already in-world, skip the menu
    }
    // Fresh login: dismiss the motd, then pick menu option 1 to enter the game.
    sendReturn();
    sendCommand("1");
    return output + readUntilQuiet();
  }

  // ----- internals -----

  private void startReader() {
    reader = new Thread(() -> {
      byte[] chunk = new byte[4096];
      try (InputStream in = socket.getInputStream()) {
        while (true) {
          int read = in.read(chunk);
          if (read <= 0) {
            break;
          }
          String text = stripIac(chunk, read);
          if (!text.isEmpty()) {
            lock.lock();
            try {
              buffer.append(text);
              lastReceivedAt = monotime();
              bufferChanged.signalAll();
            } finally {
              lock.unlock();
            }
          }
        }
      } catch (IOException e) {
        // remote closed or socket torn down — fall through
      } finally {
        lock.lock();
        try {
          closed = true;
          bufferChanged.signalAll();
        } finally {
          lock.unlock();
        }
      }
    }, "mud-session-reader");
    reader.setDaemon(true);
    reader.start();
  }

  /**
   * Telnet IAC stripper. The MUD may interleave:
   *   IAC (WILL|WONT|DO|DONT) option  — 3 bytes
   *   IAC SB option ... IAC SE        — variable
   *   IAC IAC                         — a literal 0xFF byte
   * All are discarded; CircleMUD's negotiation is mostly echo toggling around
   * the password prompt, which we don't honor.
   */
  static String stripIac(byte[] bytes, int length) {
    StringBuilder out = new StringBuilder(length);
    int i = 0;
    while (i < length) {
      int b = bytes[i] & 0xFF;
      if (b == IAC) {
        int next = (i + 1 < length) ? (bytes[i + 1] & 0xFF) : -1;
        if (next == -1) {
          break;
        } else if (next == IAC) {
          out.append((char) 0xFF);
          i += 2;
        } else if (next == WILL || next == WONT || next == DO || next == DONT) {
          i += 3;
        } else if (next == SB) {
          int j = i + 2;
          while (j < length && !((bytes[j] & 0xFF) == IAC
              && j + 1 < length && (bytes[j + 1] & 0xFF) == SE)) {
            j++;
          }
          i = j + 2;
        } else {
          i += 2;
        }
      } else {
        out.append((char) b);
        i++;
      }
    }
    return out.toString();
  }

  private void awaitQuietly(double seconds) {
    try {
      bufferChanged.await((long) (seconds * 1000), java.util.concurrent.TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new MudException("interrupted while reading");
    }
  }

  private static double monotime() {
    return System.nanoTime() / 1_000_000_000.0;
  }
}
