package com.boukensha.examples;

import com.boukensha.Boukensha;
import com.boukensha.config.Config;
import com.boukensha.tools.MudTools;

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

    String result = Boukensha.run(
        "Connect to the MUD, look at your surroundings, check your score, "
            + "then look at the available exits and tell me what you see.",
        dsl -> MudTools.register(dsl.registry(),
            config.getMudHost(), config.getMudPort(),
            config.getMudUsername(), config.getMudPassword()));

    System.out.println();
    System.out.println("=== FINAL RESPONSE ===");
    System.out.println(result);
  }
}
