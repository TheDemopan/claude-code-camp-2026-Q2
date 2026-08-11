package com.boukensha.examples;

import com.boukensha.Boukensha;
import com.boukensha.config.Config;

/**
 * Step 10 — A standard tool library (MUD demo).
 *
 * Demonstrates MudTools, which registers gameplay tools against a live CircleMUD
 * connection. Credentials come from settings.yaml (mud: host/port/username/password).
 */
public class Step10StandardToolLibrary {
  public static void main(String[] args) {
    Config config = Boukensha.config();

    System.out.println("=== BOUKENSHA Step 10: A Standard Tool Library (MUD) ===");
    System.out.println();
    System.out.println("Config:  " + config.getDir());
    System.out.println("MUD:     " + config.getMudHost() + ":" + config.getMudPort()
        + " as " + config.getMudUsername());
    System.out.println("API key set? " + (config.env("ANTHROPIC_API_KEY") != null));
    System.out.println();

    // The MUD tools register themselves from settings.yaml — Boukensha.run does it
    // via registerStandardTools, the same way Ruby's run does from its mud: keyword.
    // Registering them again here would open a second telnet session.
    String result = Boukensha.run(
        "Connect to the MUD, look at your surroundings, check your score, "
            + "then look at the available exits and tell me what you see.",
        null);

    System.out.println();
    System.out.println("=== FINAL RESPONSE ===");
    System.out.println(result);
  }
}
