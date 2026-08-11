package com.boukensha.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses CircleMUD room output into a {@link ParsedRoom}.
 *
 * Shape this targets (verified against real captured output, not assumed):
 * <pre>
 *   ESC[0;33mThe Bar Of Swordsmen ESC[0m
 *      The bar of swordsmen, once upon a time beautifully furnished.  But now the
 *   furniture is all around you in small pieces.
 *   ESC[0;36m[ Exits: s w ] ESC[0m
 *   A waiter is here.
 *   23H 100M 84V &gt;
 * </pre>
 *
 * The design rule that matters: <b>return empty rather than guess</b>. A missing
 * map entry is recoverable; a wrong one silently poisons navigation for every
 * later session. Dark rooms, combat spam, and command output that merely mentions
 * a room all produce {@code Optional.empty()}.
 */
public final class RoomParser {

  /** Require the ESC — a bare "[0m" can legitimately appear in room prose. */
  private static final Pattern ANSI = Pattern.compile("\\[[0-9;]*m");

  /** The anchor. No exits line means this output is not a room description. */
  private static final Pattern EXITS = Pattern.compile("^\\[\\s*Exits:\\s*(.*?)\\s*\\]$");

  /** Trailing status prompt, e.g. "23H 100M 84V >". */
  private static final Pattern PROMPT = Pattern.compile("^(\\d+)H\\s+(\\d+)M\\s+(\\d+)V\\s*>?\\s*$");

  private static final List<String> DIRECTIONS = List.of("n", "e", "s", "w", "u", "d");

  private RoomParser() {
  }

  public static Optional<ParsedRoom> parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }

    List<String> lines = new ArrayList<>();
    for (String line : ANSI.matcher(raw).replaceAll("").replace("\r\n", "\n").split("\n", -1)) {
      lines.add(line);
    }

    int exitsIndex = -1;
    List<String> exits = List.of();
    for (int i = 0; i < lines.size(); i++) {
      Matcher m = EXITS.matcher(lines.get(i).strip());
      if (m.matches()) {
        exitsIndex = i;
        exits = parseExits(m.group(1));
        break;
      }
    }
    if (exitsIndex < 0) {
      return Optional.empty(); // not a room description
    }

    // Name is the first non-blank line above the exits line. A dark room ("It is
    // pitch black...") has no title, so anything that reads as prose is rejected.
    String name = null;
    int nameIndex = -1;
    for (int i = 0; i < exitsIndex; i++) {
      String candidate = lines.get(i).strip();
      if (!candidate.isEmpty()) {
        name = candidate;
        nameIndex = i;
        break;
      }
    }
    if (name == null || !looksLikeRoomName(name)) {
      return Optional.empty();
    }

    StringBuilder description = new StringBuilder();
    for (int i = nameIndex + 1; i < exitsIndex; i++) {
      String line = lines.get(i).strip();
      if (line.isEmpty()) {
        continue;
      }
      if (description.length() > 0) {
        description.append(' ');
      }
      description.append(line);
    }

    List<String> contents = new ArrayList<>();
    Integer hp = null;
    Integer mana = null;
    Integer movement = null;
    for (int i = exitsIndex + 1; i < lines.size(); i++) {
      String line = lines.get(i).strip();
      if (line.isEmpty()) {
        continue;
      }
      Matcher prompt = PROMPT.matcher(line);
      if (prompt.matches()) {
        hp = Integer.parseInt(prompt.group(1));
        mana = Integer.parseInt(prompt.group(2));
        movement = Integer.parseInt(prompt.group(3));
        continue;
      }
      contents.add(line);
    }

    return Optional.of(new ParsedRoom(name, description.toString(), exits, contents,
        hp, mana, movement));
  }

  /**
   * "s w" to ["s","w"]; "None!" to empty. Only single-letter compass directions
   * are accepted — anything else is dropped rather than recorded as a exit that
   * cannot be walked.
   */
  static List<String> parseExits(String body) {
    List<String> out = new ArrayList<>();
    if (body == null || body.isBlank() || body.toLowerCase().startsWith("none")) {
      return out;
    }
    for (String token : body.trim().toLowerCase().split("\\s+")) {
      String dir = token.replaceAll("[^a-z]", "");
      if (DIRECTIONS.contains(dir) && !out.contains(dir)) {
        out.add(dir);
      }
    }
    return out;
  }

  /**
   * Guards against treating a sentence as a room title. Room names are short and
   * do not end in sentence punctuation; combat and status lines usually do.
   */
  private static boolean looksLikeRoomName(String candidate) {
    if (candidate.length() > 80) {
      return false;
    }
    char last = candidate.charAt(candidate.length() - 1);
    return last != '.' && last != '!' && last != '?';
  }
}
