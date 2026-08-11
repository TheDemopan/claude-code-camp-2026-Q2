package com.boukensha.memory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Current working state — the in-memory form of {@code state.json}.
 *
 * This is transient working state, not permanent world knowledge. Durable facts
 * belong in map.csv / rooms.jsonl / notes.md.
 */
public class AgentState {
  private static final int RECENT_PATH_LIMIT = 20;

  public Integer currentRoom;
  public Integer previousRoom;
  public List<Integer> recentPath = new ArrayList<>();
  public String currentObjective;
  /** Entries of the form "1025:w" — a room id and a direction not yet walked. */
  public Set<String> unexploredExits = new LinkedHashSet<>();

  // Last seen vitals, captured free from the MUD's status prompt.
  public Integer hp;
  public Integer mana;
  public Integer movement;

  public void enterRoom(int roomId) {
    if (currentRoom != null && currentRoom != roomId) {
      previousRoom = currentRoom;
    }
    currentRoom = roomId;
    recentPath.add(roomId);
    while (recentPath.size() > RECENT_PATH_LIMIT) {
      recentPath.remove(0);
    }
  }

  public void noteUnexplored(int roomId, List<String> directions) {
    for (String dir : directions) {
      unexploredExits.add(roomId + ":" + dir);
    }
  }

  public void clearUnexplored(int roomId, String direction) {
    unexploredExits.remove(roomId + ":" + direction);
  }
}
