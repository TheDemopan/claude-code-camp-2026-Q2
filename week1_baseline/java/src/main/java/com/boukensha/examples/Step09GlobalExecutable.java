package com.boukensha.examples;

/**
 * Step 9 — The global executable. Same REPL as step 8, reached through the
 * project's own launcher rather than a step-specific script.
 *
 * In Ruby this step installs a `boukensha` binary on the PATH; here bin/boukensha
 * is that entry point, and this class is what it runs by default.
 */
public class Step09GlobalExecutable {
  public static void main(String[] args) throws Exception {
    Step08TheReplLoop.start();
  }
}
