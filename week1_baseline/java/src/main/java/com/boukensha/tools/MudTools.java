package com.boukensha.tools;

import com.boukensha.mud.MudPrimitives;
import com.boukensha.mud.MudSession;
import com.boukensha.tool.Registry;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Registers MUD-gameplay tools against a registry.
 *
 * A single MudSession is created when the tools are registered and shared by
 * every tool — the agent logs in once and reuses the connection for all
 * subsequent tool calls.
 *
 * Tools registered, by concern:
 *   Connection  mud_connect, mud_disconnect, mud_status
 *   Perception  look, examine, check
 *   Movement    move, flee, set_position, track
 *   Combat      attack, skill_strike, consider
 *   Comms       say, tell, channel_say
 *   Inventory   get_item, drop_item, put_item, equip_item, consume_item
 *   Magic       cast_spell, use_magic_item
 *   Utility     shop, practice, save_character, send_raw
 */
public final class MudTools {

  private MudTools() {
  }

  public static MudSession register(Registry registry, String host, int port,
                                    String name, String password) {
    MudSession session = new MudSession(host, port);

    // ── Connection ────────────────────────────────────────────────────────

    registry.tool("mud_connect",
        "Open the connection to the MUD server and log in with the configured character name "
            + "and password. Safe to call when already connected (returns current status instead "
            + "of reconnecting).",
        Map.of(),
        args -> {
          if (session.isOpen()) {
            return "already connected to " + session.getHost() + ":" + session.getPort();
          }
          try {
            session.open();
            String welcome = session.login(name, password);
            return "connected to " + session.getHost() + ":" + session.getPort() + "\n" + welcome;
          } catch (MudSession.MudException e) {
            return "error: " + e.getMessage();
          }
        });

    registry.tool("mud_disconnect",
        "Close the connection to the MUD server gracefully.",
        Map.of(),
        args -> {
          if (!session.isOpen()) {
            return "already disconnected";
          }
          session.close();
          return "disconnected";
        });

    registry.tool("mud_status",
        "Return whether the MUD session is currently connected.",
        Map.of(),
        args -> session.isOpen()
            ? "connected to " + session.getHost() + ":" + session.getPort()
            : "disconnected");

    // ── Perception ────────────────────────────────────────────────────────

    registry.tool("look",
        "Look at the current room or at a specific target. Call with NO arguments to describe "
            + "the current room (do NOT pass target: 'room'). Pass a target to inspect a specific "
            + "item, mob, or player (e.g. target: 'sword'). Use preposition 'in' to look inside a "
            + "container, 'at' to inspect something, or a direction (north/east/south/west/up/down) "
            + "to peek into an adjacent room.",
        params(
            "target", "Item, mob, or player name to inspect. Omit entirely to describe the current room.",
            "preposition", "Preposition: in, at, north, east, south, west, up, down (optional)"),
        send(session, args -> MudPrimitives.look("look", str(args, "target"), str(args, "preposition"))));

    registry.tool("examine",
        "Examine a target in detail (more verbose than look).",
        params("target", "The item, mob, or player to examine"),
        send(session, args -> MudPrimitives.examine(str(args, "target"))));

    registry.tool("check",
        "Query information about your character or surroundings. Kinds: score, inventory, "
            + "equipment, gold, exits, time, weather, levels, wimpy, toggle, where.",
        params("kind", "What to check: score | inventory | equipment | gold | exits | time | "
            + "weather | levels | wimpy | toggle | where"),
        send(session, args -> MudPrimitives.infoSelf(str(args, "kind"))));

    // ── Movement ──────────────────────────────────────────────────────────

    registry.tool("move",
        "Move in a compass direction or up/down.",
        params("direction", "Direction: north | east | south | west | up | down"),
        send(session, args -> MudPrimitives.move(str(args, "direction"))));

    registry.tool("flee",
        "Attempt to flee from combat in a random available direction.",
        Map.of(),
        send(session, args -> MudPrimitives.flee()));

    registry.tool("set_position",
        "Change body position. Use 'rest' or 'sleep' between fights to recover HP and mana. "
            + "Must be standing to move or fight.",
        params("position", "Position: stand | sit | rest | sleep | wake"),
        send(session, args -> MudPrimitives.setPosition(str(args, "position"))));

    registry.tool("track",
        "Attempt to track a mob or player by name, revealing which direction they are in. "
            + "Requires the Track skill.",
        params("target", "Name of the mob or player to track"),
        send(session, args -> MudPrimitives.track(str(args, "target"))));

    // ── Combat ────────────────────────────────────────────────────────────

    registry.tool("attack",
        "Attack a target. Style 'kill' is the standard approach; 'murder' bypasses the mercy "
            + "check; 'hit' is a one-off strike.",
        params("target", "Name of the mob or player to attack",
            "style", "Attack style: kill | hit | murder (default: kill)"),
        send(session, args -> MudPrimitives.attack(
            strOr(args, "style", "kill"), str(args, "target"))));

    registry.tool("skill_strike",
        "Use a combat skill against a target.",
        params("skill", "Skill: bash | kick | backstab | rescue | assist",
            "target", "Name of the mob or player"),
        send(session, args -> MudPrimitives.skillStrike(str(args, "skill"), str(args, "target"))));

    registry.tool("consider",
        "Assess a mob's relative strength before engaging in combat. Returns a phrase such as "
            + "'You could kill it easily' or 'Death awaits you'. Always consider before attacking "
            + "an unknown mob.",
        params("target", "Name of the mob to consider"),
        send(session, args -> MudPrimitives.consider(str(args, "target"))));

    // ── Communication ─────────────────────────────────────────────────────

    registry.tool("say",
        "Speak or emote in the current room.",
        params("text", "What to say or emote",
            "mode", "Mode: say | emote | reply (default: say)"),
        send(session, args -> MudPrimitives.sayLocal(strOr(args, "mode", "say"), str(args, "text"))));

    registry.tool("tell",
        "Send a private message to a specific player.",
        params("target", "Player name to message",
            "text", "The message",
            "mode", "Mode: tell | whisper | ask (default: tell)"),
        send(session, args -> MudPrimitives.sayTargeted(
            strOr(args, "mode", "tell"), str(args, "target"), str(args, "text"))));

    registry.tool("channel_say",
        "Broadcast a message over a global channel.",
        params("channel", "Channel: shout | gossip | auction | grats | holler",
            "text", "The message to broadcast"),
        send(session, args -> MudPrimitives.sayChannel(str(args, "channel"), str(args, "text"))));

    // ── Inventory & equipment ─────────────────────────────────────────────

    registry.tool("get_item",
        "Pick up an item from the room or from a container.",
        params("item", "Name of the item to get",
            "container", "Container to get it from (optional)",
            "count", "Number of items to get (optional)"),
        send(session, args -> MudPrimitives.get(
            str(args, "item"), str(args, "container"), str(args, "count"))));

    registry.tool("drop_item",
        "Drop, donate, or junk an item.",
        params("item", "Name of the item",
            "mode", "Mode: drop | donate | junk (default: drop)",
            "count", "Number of items (optional)"),
        send(session, args -> MudPrimitives.drop(
            strOr(args, "mode", "drop"), str(args, "item"), str(args, "count"))));

    registry.tool("put_item",
        "Put an item into a container.",
        params("item", "Name of the item to put",
            "container", "Name of the container",
            "count", "Number of items (optional)"),
        send(session, args -> MudPrimitives.put(
            str(args, "item"), str(args, "container"), str(args, "count"))));

    registry.tool("equip_item",
        "Wear, wield, hold, grab, or remove an item.",
        params("item", "Name of the item",
            "action", "Action: wear | wield | hold | grab | remove",
            "body_loc", "Body location to wear on (optional, e.g. 'head', 'finger')"),
        send(session, args -> MudPrimitives.equip(
            str(args, "action"), str(args, "item"), str(args, "body_loc"))));

    registry.tool("consume_item",
        "Eat, drink, taste, or sip a consumable item.",
        params("item", "Name of the item to consume",
            "mode", "Mode: eat | drink | taste | sip (default: eat)"),
        send(session, args -> MudPrimitives.consume(strOr(args, "mode", "eat"), str(args, "item"))));

    // ── Magic ─────────────────────────────────────────────────────────────

    registry.tool("cast_spell",
        "Cast a spell, optionally at a target.",
        params("spell", "Full spell name (e.g. 'cure light wounds', 'magic missile')",
            "target", "Target mob, player, or object (optional)"),
        send(session, args -> MudPrimitives.cast(str(args, "spell"), str(args, "target"))));

    registry.tool("use_magic_item",
        "Activate a magic item: quaff a potion, recite a scroll, or use a wand/staff.",
        params("item", "Name of the item to activate",
            "mode", "Mode: quaff | recite | use",
            "target_args", "Optional target arguments (e.g. mob name for a wand)"),
        send(session, args -> MudPrimitives.useMagicItem(
            str(args, "mode"), str(args, "item"), str(args, "target_args"))));

    // ── Utility ───────────────────────────────────────────────────────────

    registry.tool("shop",
        "Interact with a shop NPC: list stock, buy, sell, or get the value of an item.",
        params("action", "Action: list | buy | sell | value | offer",
            "args", "Item name or number (optional)"),
        send(session, args -> MudPrimitives.shop(str(args, "action"), str(args, "args"))));

    registry.tool("practice",
        "List your known skills at a guildmaster, or practice a specific skill.",
        params("skill", "Skill name to practice (omit to list all)"),
        send(session, args -> MudPrimitives.practice(str(args, "skill"))));

    registry.tool("save_character",
        "Save your character to disk so progress is not lost on disconnect.",
        Map.of(),
        send(session, args -> MudPrimitives.saveChar()));

    registry.tool("send_raw",
        "Send an arbitrary command string to the MUD and return the response. Use this as an "
            + "escape hatch when no structured tool fits.",
        params("command", "The raw command to send (e.g. 'who', 'help backstab')"),
        args -> {
          String guard = guard(session);
          if (guard != null) {
            return guard;
          }
          session.sendCommand(str(args, "command"));
          return session.readUntilQuiet();
        });

    // Auto-connect at startup so the session is ready immediately and the agent
    // doesn't waste a turn calling mud_connect first.
    try {
      session.open();
      session.login(name, password);
    } catch (MudSession.MudException e) {
      System.err.println("[boukensha] MUD auto-connect failed: " + e.getMessage()
          + " — call mud_connect manually");
    }

    return session;
  }

  /**
   * Wraps a command builder: refuse when disconnected, drain stale async bytes
   * so the read sees only this command's output, send, then wait for the prompt.
   */
  private static Function<Map<String, Object>, Object> send(
      MudSession session, Function<Map<String, Object>, String> builder) {
    return args -> {
      String guard = guard(session);
      if (guard != null) {
        return guard;
      }
      String command;
      try {
        command = builder.apply(args);
      } catch (IllegalArgumentException e) {
        return "error: " + e.getMessage();
      }
      session.drain();
      session.sendCommand(command);
      return session.readUntilPrompt();
    };
  }

  private static String guard(MudSession session) {
    return session.isOpen() ? null : "error: not connected — call mud_connect first";
  }

  private static Map<String, Object> params(String... nameThenDescription) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (int i = 0; i + 1 < nameThenDescription.length; i += 2) {
      out.put(nameThenDescription[i],
          Map.of("type", "string", "description", nameThenDescription[i + 1]));
    }
    return out;
  }

  private static String str(Map<String, Object> args, String key) {
    Object value = args.get(key);
    return value == null ? null : String.valueOf(value);
  }

  private static String strOr(Map<String, Object> args, String key, String fallback) {
    String value = str(args, key);
    return (value == null || value.isBlank()) ? fallback : value;
  }
}
