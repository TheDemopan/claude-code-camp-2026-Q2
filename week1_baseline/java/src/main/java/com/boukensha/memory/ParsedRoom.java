package com.boukensha.memory;

import java.util.List;

/**
 * One room as observed in MUD output.
 *
 * Produced only when the output was confidently recognised as a room description
 * — see {@link RoomParser}. Fields are exactly what was observed; nothing here is
 * inferred.
 *
 * @param name        room title, e.g. "The Bar Of Swordsmen"
 * @param description the wrapped prose body, joined into one line
 * @param exits       observed exit letters, e.g. ["s", "w"]; empty for none
 * @param contents    NPCs, items, and objects listed after the exits line
 * @param hp          hit points from the trailing prompt, or null if absent
 * @param mana        mana from the trailing prompt, or null
 * @param movement    movement points from the trailing prompt, or null
 */
public record ParsedRoom(
    String name,
    String description,
    List<String> exits,
    List<String> contents,
    Integer hp,
    Integer mana,
    Integer movement) {

  /**
   * Stable identity key, per the schema doc's room-identification rule: match on
   * name plus the exit set, never on description wording (which varies with
   * lighting, weather, and mob presence).
   */
  public String identityKey() {
    return name.toLowerCase().strip() + "|" + String.join(",", exits);
  }
}
