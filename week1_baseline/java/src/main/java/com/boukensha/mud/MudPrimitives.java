package com.boukensha.mud;

import java.util.ArrayList;
import java.util.List;

/**
 * Stateless builders over the CircleMUD player-facing command surface. Each
 * method validates its enum-typed arguments and returns the raw line to send.
 *
 * Runtime preconditions (position, skill availability, room flags, equipment
 * requirements) are intentionally NOT checked here — they need live game state
 * and belong to the agent layer that wraps these as tools.
 */
public final class MudPrimitives {

  public static final List<String> DIRECTIONS = List.of("north", "east", "south", "west", "up", "down");
  public static final List<String> POSITIONS = List.of("stand", "sit", "rest", "sleep", "wake");
  public static final List<String> ATTACK_STYLES = List.of("hit", "murder", "kill");
  public static final List<String> STRIKE_SKILLS = List.of("backstab", "bash", "kick", "rescue", "assist");
  public static final List<String> LOCAL_SAY = List.of("say", "emote", "reply");
  public static final List<String> TARGETED_SAY = List.of("tell", "whisper", "ask");
  public static final List<String> CHANNELS = List.of("shout", "gossip", "auction", "grats", "holler");
  public static final List<String> DROP_MODES = List.of("drop", "donate", "junk");
  public static final List<String> EQUIP_OPS = List.of("wear", "wield", "grab", "hold", "remove");
  public static final List<String> CONSUME_MODES = List.of("eat", "taste", "drink", "sip");
  public static final List<String> LOOK_MODES = List.of("look", "read");
  public static final List<String> LOOK_PREPS =
      List.of("in", "at", "north", "east", "south", "west", "up", "down");
  public static final List<String> INFO_SELF = List.of("score", "inventory", "equipment", "gold",
      "exits", "time", "weather", "levels", "wimpy", "toggle", "where");
  public static final List<String> SPELL_ITEM = List.of("use", "quaff", "recite");
  public static final List<String> SHOP_OPS = List.of("buy", "sell", "list", "value", "offer");

  private MudPrimitives() {
  }

  // ---------- Movement & posture ----------

  public static String move(String direction) {
    return checkEnum(direction, DIRECTIONS, "direction");
  }

  public static String setPosition(String position) {
    return checkEnum(position, POSITIONS, "pos");
  }

  public static String flee() {
    return "flee";
  }

  public static String track(String victim) {
    return "track " + requireStr(victim, "victim");
  }

  // ---------- Combat ----------

  public static String attack(String style, String target) {
    return checkEnum(style, ATTACK_STYLES, "style") + " " + requireStr(target, "target");
  }

  public static String skillStrike(String skill, String target) {
    return checkEnum(skill, STRIKE_SKILLS, "skill") + " " + requireStr(target, "target");
  }

  public static String consider(String target) {
    return "consider " + requireStr(target, "target");
  }

  // ---------- Communication ----------

  public static String sayLocal(String mode, String text) {
    return checkEnum(mode, LOCAL_SAY, "mode") + " " + requireStr(text, "text");
  }

  public static String sayTargeted(String mode, String target, String text) {
    return checkEnum(mode, TARGETED_SAY, "mode") + " "
        + requireStr(target, "target") + " " + requireStr(text, "text");
  }

  public static String sayChannel(String channel, String text) {
    return checkEnum(channel, CHANNELS, "channel") + " " + requireStr(text, "text");
  }

  // ---------- Inventory & equipment ----------

  public static String get(String obj, String container, String count) {
    List<String> parts = new ArrayList<>();
    parts.add("get");
    if (present(count)) {
      parts.add(count);
    }
    parts.add(requireStr(obj, "obj"));
    if (present(container)) {
      parts.add(container);
    }
    return String.join(" ", parts);
  }

  public static String drop(String mode, String obj, String count) {
    List<String> parts = new ArrayList<>();
    parts.add(checkEnum(mode, DROP_MODES, "mode"));
    if (present(count)) {
      parts.add(count);
    }
    parts.add(requireStr(obj, "obj"));
    return String.join(" ", parts);
  }

  public static String put(String obj, String container, String count) {
    List<String> parts = new ArrayList<>();
    parts.add("put");
    if (present(count)) {
      parts.add(count);
    }
    parts.add(requireStr(obj, "obj"));
    parts.add(requireStr(container, "container"));
    return String.join(" ", parts);
  }

  public static String equip(String slotOp, String obj, String bodyLoc) {
    String verb = checkEnum(slotOp, EQUIP_OPS, "slot_op");
    String target = requireStr(obj, "obj");
    return present(bodyLoc) ? verb + " " + target + " " + bodyLoc : verb + " " + target;
  }

  public static String consume(String mode, String obj) {
    return checkEnum(mode, CONSUME_MODES, "mode") + " " + requireStr(obj, "obj");
  }

  // ---------- Perception ----------

  public static String look(String mode, String target, String preposition) {
    // Normalize empty strings to absent so callers can pass "" for "no value".
    String t = present(target) ? target.strip() : null;
    String prep = present(preposition) ? preposition.strip() : null;

    String verb = checkEnum(present(mode) ? mode : "look", LOOK_MODES, "mode");
    if (prep != null) {
      checkEnum(prep, LOOK_PREPS, "preposition");
    }

    List<String> parts = new ArrayList<>();
    parts.add(verb);
    if (prep != null) {
      parts.add(prep);
    }
    if (t != null) {
      parts.add(t);
    }
    return String.join(" ", parts);
  }

  public static String examine(String target) {
    return "examine " + requireStr(target, "target");
  }

  public static String infoSelf(String kind) {
    return checkEnum(kind, INFO_SELF, "kind");
  }

  // ---------- Magic ----------

  public static String cast(String spell, String target) {
    String s = requireStr(spell, "spell");
    return present(target) ? "cast '" + s + "' " + target : "cast '" + s + "'";
  }

  public static String useMagicItem(String mode, String item, String targetArgs) {
    String verb = checkEnum(mode, SPELL_ITEM, "mode");
    String it = requireStr(item, "item");
    return present(targetArgs) ? verb + " " + it + " " + targetArgs : verb + " " + it;
  }

  // ---------- Utility ----------

  public static String shop(String op, String args) {
    String verb = checkEnum(op, SHOP_OPS, "op");
    return present(args) ? verb + " " + args : verb;
  }

  public static String practice(String skill) {
    return present(skill) ? "practice " + skill : "practice";
  }

  public static String saveChar() {
    return "save";
  }

  // ---------- validation ----------

  static String checkEnum(String value, List<String> allowed, String label) {
    String v = value == null ? "" : value.strip().toLowerCase();
    if (!allowed.contains(v)) {
      throw new IllegalArgumentException(
          label + " must be one of: " + String.join(", ", allowed) + " (got " + value + ")");
    }
    return v;
  }

  static String requireStr(String value, String label) {
    if (!present(value)) {
      throw new IllegalArgumentException(label + " is required");
    }
    return value.strip();
  }

  private static boolean present(String value) {
    return value != null && !value.isBlank();
  }
}
