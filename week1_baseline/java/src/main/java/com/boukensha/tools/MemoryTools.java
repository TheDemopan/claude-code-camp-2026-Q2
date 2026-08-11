package com.boukensha.tools;

import com.boukensha.memory.AgentState;
import com.boukensha.memory.MemoryStore;
import com.boukensha.memory.RoomGraph;
import com.boukensha.tool.Registry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The memory tools the agent actually needs.
 *
 * Topology is captured automatically by {@link com.boukensha.memory.MemoryRecorder},
 * so nothing here asks the model to record rooms or exits — the journal shows that
 * kind of bookkeeping discipline is exactly what models fail at. These cover only
 * what genuinely needs judgment, plus route-finding, which replaces spatial
 * reasoning with a graph search.
 */
public final class MemoryTools {

  private MemoryTools() {
  }

  public static void register(Registry registry, MemoryStore store) {

    registry.tool("find_route",
        "Find the way to a room you have already visited. Give the room name (or part of it) "
            + "and this returns the exact directions to walk, e.g. 'north, north, west'. "
            + "Prefer this over guessing directions — it uses the map built as you explored.",
        Map.of("destination", Map.of("type", "string",
            "description", "Name (or part of the name) of the room to travel to")),
        args -> {
          AgentState state = store.state();
          if (state.currentRoom == null) {
            return "error: current location unknown — look around first";
          }
          String query = String.valueOf(args.get("destination"));
          Optional<RoomGraph.Room> target = findRoom(store.graph(), query);
          if (target.isEmpty()) {
            return "No room matching '" + query + "' in memory. Rooms known: "
                + store.graph().size() + ". Use map_summary to see them.";
          }
          if (target.get().id == state.currentRoom) {
            return "You are already in " + target.get().name + ".";
          }
          List<String> route = store.graph().findRoute(state.currentRoom, target.get().id);
          if (route.isEmpty()) {
            return "No known route from here to " + target.get().name
                + ". The connecting rooms have not been explored yet.";
          }
          List<String> longNames = new ArrayList<>();
          route.forEach(d -> longNames.add(RoomGraph.longName(d)));
          return "Route to " + target.get().name + " (" + route.size() + " moves): "
              + String.join(", ", longNames);
        });

    registry.tool("map_summary",
        "Summarise what you have mapped: where you are, nearby rooms, and which exits you "
            + "have seen but not yet walked through. Use this to decide where to explore next.",
        Map.of(),
        args -> {
          RoomGraph graph = store.graph();
          AgentState state = store.state();
          StringBuilder out = new StringBuilder();
          out.append("Rooms mapped: ").append(graph.size()).append('\n');

          if (state.currentRoom != null) {
            graph.get(state.currentRoom).ifPresent(room -> {
              out.append("You are in: ").append(room.name).append(" (id ").append(room.id).append(")\n");
              out.append("Exits here: ").append(describeExits(graph, room)).append('\n');
            });
          }
          if (state.currentObjective != null && !state.currentObjective.isBlank()) {
            out.append("Objective: ").append(state.currentObjective).append('\n');
          }

          if (!state.unexploredExits.isEmpty()) {
            out.append("\nUnexplored exits (").append(state.unexploredExits.size()).append("):\n");
            int shown = 0;
            for (String entry : state.unexploredExits) {
              if (shown++ >= 12) {
                out.append("  … and ").append(state.unexploredExits.size() - 12).append(" more\n");
                break;
              }
              String[] parts = entry.split(":");
              String roomName = graph.get(Integer.parseInt(parts[0]))
                  .map(r -> r.name).orElse("room " + parts[0]);
              out.append("  · ").append(RoomGraph.longName(parts[1]))
                  .append(" from ").append(roomName).append('\n');
            }
          }

          out.append("\nKnown rooms:\n");
          int shown = 0;
          for (RoomGraph.Room room : graph.rooms()) {
            if (shown++ >= 30) {
              out.append("  … and ").append(graph.size() - 30).append(" more\n");
              break;
            }
            out.append("  ").append(room.id).append(". ").append(room.name).append('\n');
          }
          return out.toString();
        });

    registry.tool("recall_room",
        "Recall what you know about a room you have visited — its description, what was there, "
            + "and its exits. Use before travelling somewhere to remind yourself what to expect.",
        Map.of("name", Map.of("type", "string",
            "description", "Name (or part of the name) of the room to recall")),
        args -> {
          String query = String.valueOf(args.get("name"));
          Optional<RoomGraph.Room> found = findRoom(store.graph(), query);
          if (found.isEmpty()) {
            return "No memory of a room matching '" + query + "'.";
          }
          RoomGraph.Room room = found.get();
          StringBuilder out = new StringBuilder();
          out.append(room.name).append(" (id ").append(room.id).append(")\n");
          out.append("Exits: ").append(describeExits(store.graph(), room)).append('\n');
          store.detail(room.id).ifPresent(detail -> {
            if (detail.description != null && !detail.description.isBlank()) {
              out.append("Description: ").append(detail.description).append('\n');
            }
            if (!detail.npcs.isEmpty()) {
              out.append("Seen here: ").append(String.join("; ", detail.npcs)).append('\n');
            }
          });
          return out.toString();
        });

    registry.tool("set_objective",
        "Record what you are currently trying to achieve, so it survives across sessions. "
            + "Replaces any previous objective.",
        Map.of("objective", Map.of("type", "string",
            "description", "The goal, e.g. 'Buy food from the baker'")),
        args -> {
          String objective = String.valueOf(args.get("objective"));
          store.state().currentObjective = objective;
          store.save();
          return "Objective set: " + objective;
        });

    registry.tool("remember_note",
        "Save a durable fact worth keeping across sessions that does not belong to one room — "
            + "an NPC's role, a price, a danger, a shortcut. Keep it to one sentence.",
        Map.of("note", Map.of("type", "string", "description", "The fact to remember")),
        args -> {
          String note = String.valueOf(args.get("note"));
          store.appendNote(note);
          return "Noted: " + note;
        });
  }

  /** Exact name match first, then a unique substring match. */
  private static Optional<RoomGraph.Room> findRoom(RoomGraph graph, String query) {
    if (query == null || query.isBlank()) {
      return Optional.empty();
    }
    String needle = query.strip().toLowerCase();
    for (RoomGraph.Room room : graph.rooms()) {
      if (room.name.equalsIgnoreCase(needle)) {
        return Optional.of(room);
      }
    }
    List<RoomGraph.Room> partial = new ArrayList<>();
    for (RoomGraph.Room room : graph.rooms()) {
      if (room.name.toLowerCase().contains(needle)) {
        partial.add(room);
      }
    }
    return partial.size() == 1 ? Optional.of(partial.get(0))
        : partial.isEmpty() ? Optional.empty() : Optional.of(partial.get(0));
  }

  private static String describeExits(RoomGraph graph, RoomGraph.Room room) {
    if (room.exits.isEmpty()) {
      return "none";
    }
    Map<String, String> described = new LinkedHashMap<>();
    room.exits.forEach((dir, dest) -> {
      if (RoomGraph.UNEXPLORED.equals(dest)) {
        described.put(dir, "unexplored");
      } else {
        String name = graph.get(Integer.parseInt(dest)).map(r -> r.name).orElse("room " + dest);
        described.put(dir, name);
      }
    });
    List<String> parts = new ArrayList<>();
    described.forEach((dir, where) -> parts.add(RoomGraph.longName(dir) + " → " + where));
    return String.join(", ", parts);
  }
}
