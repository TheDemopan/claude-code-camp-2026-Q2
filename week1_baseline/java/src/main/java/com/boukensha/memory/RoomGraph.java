package com.boukensha.memory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The navigation graph — the in-memory form of {@code map.csv}.
 *
 * The exit graph is authoritative. Coordinates are optional visualisation
 * metadata and must never be used to infer a connection.
 */
public class RoomGraph {

  /** Marks an exit that is known to exist but has not been traversed. */
  public static final String UNEXPLORED = "?";

  public static final List<String> DIRECTIONS = List.of("n", "e", "s", "w", "u", "d");

  private static final Map<String, String> LONG_NAME = Map.of(
      "n", "north", "e", "east", "s", "south", "w", "west", "u", "up", "d", "down");

  /** One room. {@code exits} maps a direction to a destination id or {@link #UNEXPLORED}. */
  public static class Room {
    public final int id;
    public String area;
    public String name;
    public final Map<String, String> exits = new LinkedHashMap<>();
    public Integer x;
    public Integer y;
    public Integer z;

    Room(int id, String area, String name) {
      this.id = id;
      this.area = area;
      this.name = name;
    }

    /** Directions known to exist but not yet walked. */
    public List<String> unexploredDirections() {
      List<String> out = new ArrayList<>();
      exits.forEach((dir, dest) -> {
        if (UNEXPLORED.equals(dest)) {
          out.add(dir);
        }
      });
      return out;
    }
  }

  private final Map<Integer, Room> rooms = new TreeMap<>();
  private int nextId = 1;

  public Collection<Room> rooms() {
    return rooms.values();
  }

  public int size() {
    return rooms.size();
  }

  public Optional<Room> get(int id) {
    return Optional.ofNullable(rooms.get(id));
  }

  /**
   * Room identification per the schema doc: match on name plus the observed exit
   * set. Description wording is deliberately ignored — it varies with lighting,
   * weather, and mob presence, and matching on it creates false merges.
   */
  public Optional<Room> findByIdentity(String name, List<String> exits) {
    for (Room room : rooms.values()) {
      if (!room.name.equalsIgnoreCase(name)) {
        continue;
      }
      if (exitLettersOf(room).equals(new ArrayList<>(exits))) {
        return Optional.of(room);
      }
    }
    return Optional.empty();
  }

  /**
   * Return the existing room matching this identity, or create a new one.
   *
   * When identity is uncertain we create rather than merge — the schema doc's
   * "preserve the uncertainty" rule. A duplicate room is a cosmetic problem; a
   * wrong merge silently corrupts every route through it.
   */
  public Room addOrGet(String name, String area, List<String> exits) {
    Optional<Room> existing = findByIdentity(name, exits);
    if (existing.isPresent()) {
      return existing.get();
    }
    Room room = new Room(nextId++, area, name);
    for (String dir : exits) {
      room.exits.put(dir, UNEXPLORED);
    }
    rooms.put(room.id, room);
    return room;
  }

  /**
   * Record a traversed edge. Only the direction actually walked is written —
   * the reverse is never assumed, per the schema doc.
   */
  public void setExit(int fromId, String direction, int toId) {
    Room from = rooms.get(fromId);
    if (from == null || !DIRECTIONS.contains(direction)) {
      return;
    }
    from.exits.put(direction, String.valueOf(toId));
  }

  /** Add any newly observed exits as unexplored, without clobbering known edges. */
  public void mergeObservedExits(Room room, List<String> observed) {
    for (String dir : observed) {
      room.exits.putIfAbsent(dir, UNEXPLORED);
    }
  }

  /**
   * Breadth-first route between two rooms, as a direction list.
   *
   * Returns empty when no known path exists — never a partial or invented one.
   * Unexplored exits are not traversable: we do not know where they lead.
   */
  public List<String> findRoute(int fromId, int toId) {
    if (fromId == toId || !rooms.containsKey(fromId) || !rooms.containsKey(toId)) {
      return List.of();
    }
    Map<Integer, String> cameByDirection = new HashMap<>();
    Map<Integer, Integer> cameFrom = new HashMap<>();
    Deque<Integer> queue = new ArrayDeque<>();
    queue.add(fromId);
    cameFrom.put(fromId, null);

    while (!queue.isEmpty()) {
      int current = queue.poll();
      if (current == toId) {
        return reconstruct(cameFrom, cameByDirection, toId);
      }
      Room room = rooms.get(current);
      if (room == null) {
        continue;
      }
      for (Map.Entry<String, String> exit : room.exits.entrySet()) {
        String dest = exit.getValue();
        if (dest == null || UNEXPLORED.equals(dest) || dest.isBlank()) {
          continue;
        }
        int destId;
        try {
          destId = Integer.parseInt(dest);
        } catch (NumberFormatException e) {
          continue;
        }
        if (cameFrom.containsKey(destId) || !rooms.containsKey(destId)) {
          continue;
        }
        cameFrom.put(destId, current);
        cameByDirection.put(destId, exit.getKey());
        queue.add(destId);
      }
    }
    return List.of();
  }

  private List<String> reconstruct(Map<Integer, Integer> cameFrom,
                                   Map<Integer, String> cameByDirection, int target) {
    List<String> path = new ArrayList<>();
    Integer cursor = target;
    while (cameFrom.get(cursor) != null) {
      path.add(0, cameByDirection.get(cursor));
      cursor = cameFrom.get(cursor);
    }
    return path;
  }

  public static String longName(String direction) {
    return LONG_NAME.getOrDefault(direction, direction);
  }

  // ---------- map.csv ----------

  public static final String CSV_HEADER = "id,area,name,n,e,s,w,u,d,x,y,z";

  public String toCsv() {
    StringBuilder out = new StringBuilder(CSV_HEADER).append('\n');
    for (Room room : rooms.values()) {
      out.append(room.id).append(',')
          .append(escape(room.area)).append(',')
          .append(escape(room.name));
      for (String dir : DIRECTIONS) {
        out.append(',').append(room.exits.getOrDefault(dir, ""));
      }
      out.append(',').append(room.x == null ? "" : room.x)
          .append(',').append(room.y == null ? "" : room.y)
          .append(',').append(room.z == null ? "" : room.z)
          .append('\n');
    }
    return out.toString();
  }

  /** Malformed rows are skipped rather than silently producing a broken room. */
  public static RoomGraph fromCsv(String csv) {
    RoomGraph graph = new RoomGraph();
    if (csv == null || csv.isBlank()) {
      return graph;
    }
    for (String line : csv.split("\n")) {
      if (line.isBlank() || line.startsWith("id,")) {
        continue;
      }
      List<String> cells = splitCsv(line);
      if (cells.size() < 9) {
        continue;
      }
      int id;
      try {
        id = Integer.parseInt(cells.get(0).trim());
      } catch (NumberFormatException e) {
        continue;
      }
      Room room = new Room(id, cells.get(1), cells.get(2));
      for (int i = 0; i < DIRECTIONS.size(); i++) {
        String value = cells.get(3 + i).trim();
        if (!value.isEmpty()) {
          room.exits.put(DIRECTIONS.get(i), value);
        }
      }
      room.x = parseIntOrNull(cells, 9);
      room.y = parseIntOrNull(cells, 10);
      room.z = parseIntOrNull(cells, 11);
      graph.rooms.put(id, room);
      graph.nextId = Math.max(graph.nextId, id + 1);
    }
    return graph;
  }

  private static Integer parseIntOrNull(List<String> cells, int index) {
    if (index >= cells.size()) {
      return null;
    }
    String value = cells.get(index).trim();
    try {
      return value.isEmpty() ? null : Integer.valueOf(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private List<String> exitLettersOf(Room room) {
    List<String> out = new ArrayList<>();
    for (String dir : DIRECTIONS) {
      if (room.exits.containsKey(dir)) {
        out.add(dir);
      }
    }
    return out;
  }

  private static String escape(String value) {
    String v = value == null ? "" : value;
    return v.contains(",") || v.contains("\"")
        ? "\"" + v.replace("\"", "\"\"") + "\""
        : v;
  }

  static List<String> splitCsv(String line) {
    List<String> cells = new ArrayList<>();
    StringBuilder cell = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (quoted) {
        if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
          cell.append('"');
          i++;
        } else if (c == '"') {
          quoted = false;
        } else {
          cell.append(c);
        }
      } else if (c == '"') {
        quoted = true;
      } else if (c == ',') {
        cells.add(cell.toString());
        cell.setLength(0);
      } else {
        cell.append(c);
      }
    }
    cells.add(cell.toString());
    return cells;
  }
}
