package com.boukensha.memory;

import java.util.ArrayList;
import java.util.List;

/**
 * Verbose per-room observations — one {@code rooms.jsonl} record.
 *
 * Deliberately does NOT carry name, area, or exits: those live in map.csv and
 * duplicating them lets the two files drift apart.
 */
public class RoomDetail {
  public int id;
  public String description;
  public List<String> landmarks = new ArrayList<>();
  public List<String> npcs = new ArrayList<>();
  public List<String> items = new ArrayList<>();
  public List<String> shops = new ArrayList<>();
  public String notes;

  public RoomDetail() {
  }

  public RoomDetail(int id, String description) {
    this.id = id;
    this.description = description;
  }

  /**
   * Fold a fresh observation of the room's contents in, keeping the union.
   * Contents vary between visits (mobs wander, items get taken), so this
   * accumulates rather than replaces — but only up to a cap, so a busy room
   * cannot grow the file without bound.
   */
  public void observeContents(List<String> contents) {
    for (String line : contents) {
      if (line == null || line.isBlank()) {
        continue;
      }
      String trimmed = line.strip();
      if (!npcs.contains(trimmed) && npcs.size() < 25) {
        npcs.add(trimmed);
      }
    }
  }
}
