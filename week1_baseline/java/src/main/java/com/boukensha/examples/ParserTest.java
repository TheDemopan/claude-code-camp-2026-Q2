package com.boukensha.examples;

import com.boukensha.memory.ParsedRoom;
import com.boukensha.memory.RoomParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Exercises {@link RoomParser} against real captured MUD output.
 *
 * Two halves: fixed assertions on strings taken verbatim from a live session, and
 * a replay over every look/move tool result in the session logs to confirm the
 * parser neither crashes nor invents rooms on real traffic.
 */
public class ParserTest {

  // Verbatim from .boukensha/sessions — including ANSI codes and CRLF.
  private static final String REAL_LOOK =
      "[0;33mThe Bar Of Swordsmen[0m\r\n"
          + "   The bar of swordsmen, once upon a time beautifully furnished.  But now the\r\n"
          + "furniture is all around you in small pieces.  To the south is the yard, and\r\n"
          + "to the west is the entrance hall.\r\n"
          + "[0;36m[ Exits: s w ][0m\r\n"
          + "[0;32mA large, sociable bulletin board is mounted on a wall here.\r\n"
          + "A waiter is here.\r\n"
          + "23H 100M 84V > ";

  private static final String REAL_MOVE =
      "[0;33mThe Tournament And Practice Yard[0m\r\n"
          + "   This is the practice yard of the fighters.  To the north is the bar.\r\n"
          + "A well leads down into darkness.\r\n"
          + "[0;36m[ Exits: n d ][0m\r\n"
          + "[0;33mYour guildmaster is standing here sharpening an axe.\r\n"
          + "[0m\r\n"
          + "23H 100M 84V > ";

  private static int failures = 0;

  public static void main(String[] args) throws IOException {
    System.out.println("=== RoomParser — real captured output ===\n");

    ParsedRoom bar = RoomParser.parse(REAL_LOOK).orElseThrow(
        () -> new AssertionError("failed to parse a known-good look response"));
    check("look: name", "The Bar Of Swordsmen", bar.name());
    check("look: exits", List.of("s", "w"), bar.exits());
    checkTrue("look: description joined + unwrapped",
        bar.description().startsWith("The bar of swordsmen, once upon a time")
            && bar.description().endsWith("entrance hall."));
    checkTrue("look: no ANSI leaked into description", !bar.description().contains(""));
    check("look: contents count", 2, bar.contents().size());
    check("look: hp from prompt", 23, bar.hp());
    check("look: mana from prompt", 100, bar.mana());
    check("look: movement from prompt", 84, bar.movement());
    checkTrue("look: prompt excluded from contents",
        bar.contents().stream().noneMatch(c -> c.contains("100M")));

    ParsedRoom yard = RoomParser.parse(REAL_MOVE).orElseThrow(
        () -> new AssertionError("failed to parse a known-good move response"));
    check("move: name", "The Tournament And Practice Yard", yard.name());
    check("move: exits (incl. down)", List.of("n", "d"), yard.exits());
    check("move: contents", 1, yard.contents().size());

    checkTrue("identity keys differ between rooms",
        !bar.identityKey().equals(yard.identityKey()));

    System.out.println("\n--- rejects non-room output (writes nothing rather than junk) ---");
    checkEmpty("empty string", RoomParser.parse(""));
    checkEmpty("null", RoomParser.parse(null));
    checkEmpty("score output (no exits line)",
        RoomParser.parse("You are 17 years old.\r\nYou have 23(23) hit points.\r\n23H 100M 84V > "));
    checkEmpty("dark room (prose, no title)",
        RoomParser.parse("It is pitch black...\r\n[0;36m[ Exits: n ][0m\r\n"));
    checkEmpty("bare prompt", RoomParser.parse("23H 100M 84V > "));

    System.out.println("\n--- 'Exits: None!' yields a room with zero exits, not a reject ---");
    Optional<ParsedRoom> noExits = RoomParser.parse(
        "[0;33mA Sealed Vault[0m\r\n   Solid rock.\r\n[ Exits: None! ]\r\n");
    checkTrue("sealed room still parses", noExits.isPresent());
    noExits.ifPresent(r -> check("sealed room: zero exits", 0, r.exits().size()));

    replayRealSessions();

    System.out.println();
    if (failures == 0) {
      System.out.println("ALL CHECKS PASSED");
    } else {
      System.out.println(failures + " CHECK(S) FAILED");
      System.exit(1);
    }
  }

  /** Replay every look/move result ever logged; the parser must survive all of it. */
  private static void replayRealSessions() throws IOException {
    Path dir = Paths.get(System.getProperty("user.home"),
        "claude-code-camp-2026-Q2", ".boukensha", "sessions");
    if (!Files.isDirectory(dir)) {
      System.out.println("\n--- session replay skipped (no sessions dir) ---");
      return;
    }

    ObjectMapper mapper = new ObjectMapper();
    int parsed = 0;
    int rejected = 0;
    java.util.Set<String> rooms = new java.util.TreeSet<>();

    List<Path> logs;
    try (Stream<Path> files = Files.list(dir)) {
      logs = files.filter(p -> p.toString().endsWith(".jsonl"))
          .sorted(Comparator.comparing(Path::toString))
          .toList();
    }

    for (Path log : logs) {
      for (String line : Files.readAllLines(log)) {
        JsonNode event;
        try {
          event = mapper.readTree(line);
        } catch (Exception e) {
          continue;
        }
        if (!"tool_result".equals(event.path("phase").asText())) {
          continue;
        }
        String name = event.path("name").asText();
        if (!name.equals("look") && !name.equals("move")) {
          continue;
        }
        Optional<ParsedRoom> room = RoomParser.parse(event.path("result").asText());
        if (room.isPresent()) {
          parsed++;
          rooms.add(room.get().name());
        } else {
          rejected++;
        }
      }
    }

    System.out.println("\n--- replay over real session logs ---");
    System.out.println("  parsed:   " + parsed + " room observations");
    System.out.println("  rejected: " + rejected + " (non-room look/move output)");
    System.out.println("  distinct rooms discovered:");
    rooms.forEach(r -> System.out.println("    · " + r));
    checkTrue("replay found at least one room", !rooms.isEmpty());
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

  private static void checkEmpty(String label, Optional<ParsedRoom> actual) {
    boolean ok = actual.isEmpty();
    if (!ok) {
      failures++;
    }
    System.out.println((ok ? "  PASS  " : "  FAIL  ") + "rejects " + label
        + (ok ? "" : "  (got " + actual.get().name() + ")"));
  }
}
