package com.boukensha.memory;

import com.boukensha.model.Context;
import com.boukensha.tool.Registry;
import com.boukensha.tool.Tool;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Automatic topology capture.
 *
 * Rather than hooking inside MudTools — which steps 10, 11 and 12 depend on — this
 * <b>decorates</b> the already-registered {@code look} and {@code move} tools:
 * {@code Registry.tool()} overwrites by name and {@link Tool#getBlock()} exposes
 * the original, so the wrapper calls through, folds the response into the store,
 * and returns it unchanged. The model sees identical tools; the MUD sees
 * identical traffic; no existing file is modified.
 *
 * Consequence: the model needs no discipline whatsoever to build the map. It
 * cannot forget, because it is never asked.
 */
public class MemoryRecorder {

  private final MemoryStore store;
  private final String area;

  public MemoryRecorder(MemoryStore store, String area) {
    this.store = store;
    this.area = area == null ? "unknown" : area;
  }

  /**
   * Wrap the movement and perception tools. Must be called <b>after</b>
   * MudTools.register — it throws rather than silently no-opping, because a
   * silent no-op would look exactly like working memory that records nothing.
   */
  public static MemoryRecorder wrap(Registry registry, Context context, MemoryStore store,
                                    String area) {
    if (!registry.hasTool("move") || !registry.hasTool("look")) {
      throw new IllegalStateException(
          "MemoryRecorder.wrap must run after MudTools.register — no move/look tool found");
    }
    MemoryRecorder recorder = new MemoryRecorder(store, area);

    Tool move = context.getTools().get("move");
    Function<Map<String, Object>, Object> moveBlock = move.getBlock();
    registry.tool(move.getName(), move.getDescription(), move.getParameters(), args -> {
      Object result = moveBlock.apply(args);
      recorder.recordMove(String.valueOf(args.get("direction")), String.valueOf(result));
      return result;
    });

    Tool look = context.getTools().get("look");
    Function<Map<String, Object>, Object> lookBlock = look.getBlock();
    registry.tool(look.getName(), look.getDescription(), look.getParameters(), args -> {
      Object result = lookBlock.apply(args);
      // Only a bare look describes the room we are standing in; look at/in
      // something describes that thing instead and must not be mapped.
      Object target = args.get("target");
      Object prep = args.get("preposition");
      boolean bare = (target == null || String.valueOf(target).isBlank())
          && (prep == null || String.valueOf(prep).isBlank());
      if (bare) {
        recorder.recordLook(String.valueOf(result));
      }
      return result;
    });

    return recorder;
  }

  /** A bare look: refresh where we are, without asserting any new edge. */
  public synchronized void recordLook(String response) {
    Optional<ParsedRoom> parsed = RoomParser.parse(response);
    if (parsed.isEmpty()) {
      return; // not a room; write nothing rather than junk
    }
    ParsedRoom room = parsed.get();
    RoomGraph.Room known = store.graph().addOrGet(room.name(), area, room.exits());
    store.graph().mergeObservedExits(known, room.exits());
    absorb(known, room);

    AgentState state = store.state();
    if (state.currentRoom == null || state.currentRoom != known.id) {
      state.enterRoom(known.id);
    }
    state.noteUnexplored(known.id, known.unexploredDirections());
    store.save();
  }

  /**
   * A traversal: identify the destination and record the edge actually walked.
   *
   * The reverse edge is deliberately NOT written. Moving north then south does
   * not reliably return you to the same room — one-way passages, portals and
   * teleports all exist — so the reverse is only ever learned by walking it.
   */
  public synchronized void recordMove(String direction, String response) {
    String dir = normalizeDirection(direction);
    Optional<ParsedRoom> parsed = RoomParser.parse(response);
    if (dir == null || parsed.isEmpty()) {
      return; // blocked move, closed door, or non-room output
    }
    ParsedRoom room = parsed.get();
    AgentState state = store.state();
    Integer from = state.currentRoom;

    RoomGraph.Room destination = store.graph().addOrGet(room.name(), area, room.exits());
    store.graph().mergeObservedExits(destination, room.exits());
    absorb(destination, room);

    if (from != null && from != destination.id) {
      store.graph().setExit(from, dir, destination.id);
      state.clearUnexplored(from, dir);
    }
    state.enterRoom(destination.id);
    state.noteUnexplored(destination.id, destination.unexploredDirections());
    store.save();
  }

  /** Fold observations into the detail record and the free vitals from the prompt. */
  private void absorb(RoomGraph.Room known, ParsedRoom observed) {
    RoomDetail detail = store.detailOrCreate(known.id, observed.description());
    if (detail.description == null || detail.description.isBlank()) {
      detail.description = observed.description();
    }
    detail.observeContents(observed.contents());

    AgentState state = store.state();
    if (observed.hp() != null) {
      state.hp = observed.hp();
      state.mana = observed.mana();
      state.movement = observed.movement();
    }
  }

  /** Accepts "north" or "n"; returns null for anything not a compass direction. */
  static String normalizeDirection(String raw) {
    if (raw == null) {
      return null;
    }
    String value = raw.strip().toLowerCase();
    if (value.isEmpty()) {
      return null;
    }
    List<String> dirs = RoomGraph.DIRECTIONS;
    if (dirs.contains(value)) {
      return value;
    }
    String initial = value.substring(0, 1);
    return dirs.contains(initial) && RoomGraph.longName(initial).equals(value) ? initial : null;
  }
}
