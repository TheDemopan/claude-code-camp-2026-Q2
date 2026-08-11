package com.boukensha.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Owns the four memory files described in docs/plans/java_port/memory_implementation.md:
 *
 * <pre>
 *   memory/map.csv      navigation graph (authoritative topology)
 *   memory/rooms.jsonl  verbose per-room observations
 *   memory/state.json   current working state
 *   memory/notes.md     durable global knowledge
 * </pre>
 *
 * Each file has one purpose and facts are not duplicated across them. Writes are
 * atomic (temp file then move) so a crash mid-save cannot leave a half-written
 * map behind.
 */
public class MemoryStore {

  private final Path dir;
  private final Path mapFile;
  private final Path roomsFile;
  private final Path stateFile;
  private final Path notesFile;
  private final ObjectMapper mapper = new ObjectMapper();

  private RoomGraph graph;
  private AgentState state;
  private final Map<Integer, RoomDetail> details = new LinkedHashMap<>();

  public MemoryStore(Path dir) {
    this.dir = dir;
    this.mapFile = dir.resolve("map.csv");
    this.roomsFile = dir.resolve("rooms.jsonl");
    this.stateFile = dir.resolve("state.json");
    this.notesFile = dir.resolve("notes.md");
    mapper.enable(SerializationFeature.INDENT_OUTPUT);
    load();
  }

  public RoomGraph graph() {
    return graph;
  }

  public AgentState state() {
    return state;
  }

  public Path dir() {
    return dir;
  }

  public Optional<RoomDetail> detail(int roomId) {
    return Optional.ofNullable(details.get(roomId));
  }

  public RoomDetail detailOrCreate(int roomId, String description) {
    return details.computeIfAbsent(roomId, id -> new RoomDetail(id, description));
  }

  public String notes() {
    return readOrEmpty(notesFile);
  }

  /** Append durable global knowledge. Kept as prose; the caller keeps it concise. */
  public void appendNote(String note) {
    if (note == null || note.isBlank()) {
      return;
    }
    String existing = notes();
    StringBuilder out = new StringBuilder();
    if (existing.isBlank()) {
      out.append("# Boukensha notes\n\nDurable knowledge that does not belong to a single room.\n");
    } else {
      out.append(existing.stripTrailing()).append('\n');
    }
    out.append("\n- ").append(LocalDate.now()).append(" — ").append(note.strip()).append('\n');
    writeAtomic(notesFile, out.toString());
  }

  // ---------- persistence ----------

  private void load() {
    graph = RoomGraph.fromCsv(readOrEmpty(mapFile));

    String stateJson = readOrEmpty(stateFile);
    if (stateJson.isBlank()) {
      state = new AgentState();
    } else {
      try {
        state = mapper.readValue(stateJson, AgentState.class);
      } catch (IOException e) {
        // A corrupt state file must not stop the agent — the map is the valuable
        // part and it lives in a separate file.
        System.err.println("[memory] state.json unreadable, starting fresh: " + e.getMessage());
        state = new AgentState();
      }
    }

    for (String line : readOrEmpty(roomsFile).split("\n")) {
      if (line.isBlank()) {
        continue;
      }
      try {
        RoomDetail detail = mapper.readValue(line, RoomDetail.class);
        details.put(detail.id, detail);
      } catch (IOException e) {
        // Skip the malformed record rather than discarding the whole file.
      }
    }
  }

  public void save() {
    try {
      Files.createDirectories(dir);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    writeAtomic(mapFile, graph.toCsv());

    StringBuilder rooms = new StringBuilder();
    for (RoomDetail detail : details.values()) {
      try {
        rooms.append(mapper.writer().withoutFeatures(SerializationFeature.INDENT_OUTPUT)
            .writeValueAsString(detail)).append('\n');
      } catch (IOException e) {
        // drop this record rather than corrupt the file
      }
    }
    writeAtomic(roomsFile, rooms.toString());

    try {
      writeAtomic(stateFile, mapper.writeValueAsString(state));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private String readOrEmpty(Path path) {
    try {
      return Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : "";
    } catch (IOException e) {
      return "";
    }
  }

  /** Write via a temp file and move, so a crash cannot leave a partial map. */
  private void writeAtomic(Path target, String content) {
    try {
      Files.createDirectories(target.getParent());
      Path temp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
      Files.writeString(temp, content, StandardCharsets.UTF_8);
      Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * A compact brief for the system prompt: where we are, what we're doing, and
   * what is nearby. Deliberately bounded — the schema doc is explicit that the
   * store may grow large and only the relevant subset should enter context.
   */
  public String contextBrief() {
    StringBuilder out = new StringBuilder();
    out.append("## Memory\n\n");
    out.append("You have persistent memory of this world across sessions. ")
        .append("Rooms and exits are recorded automatically as you move — you do not need to ")
        .append("record them yourself. Use find_route to navigate to somewhere you have been ")
        .append("rather than guessing directions.\n\n");
    out.append("Rooms mapped so far: ").append(graph.size()).append('\n');

    if (state.currentRoom != null) {
      graph.get(state.currentRoom).ifPresent(room -> {
        out.append("Current room: ").append(room.name)
            .append(" (id ").append(room.id).append(")\n");
        String unexplored = String.join(", ", room.unexploredDirections());
        if (!unexplored.isBlank()) {
          out.append("Unexplored exits here: ").append(unexplored).append('\n');
        }
      });
    }
    if (state.currentObjective != null && !state.currentObjective.isBlank()) {
      out.append("Current objective: ").append(state.currentObjective).append('\n');
    }
    if (!state.unexploredExits.isEmpty()) {
      out.append("Known unexplored exits elsewhere: ")
          .append(state.unexploredExits.size()).append(" (see map_summary)\n");
    }

    String notes = notes();
    if (!notes.isBlank()) {
      out.append("\nNotes:\n").append(notes.length() > 1200
          ? notes.substring(0, 1200) + "\n…(truncated)" : notes);
    }
    return out.toString();
  }
}
