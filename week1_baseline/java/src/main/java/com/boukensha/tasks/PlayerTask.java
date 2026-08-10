package com.boukensha.tasks;

/** Port of Tasks::Player. */
public final class PlayerTask extends Task {
  public static final PlayerTask INSTANCE = new PlayerTask();

  private PlayerTask() {
    super("player");
  }
}
