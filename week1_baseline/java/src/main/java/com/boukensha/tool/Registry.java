package com.boukensha.tool;

import com.boukensha.exception.UnknownToolError;
import com.boukensha.model.Context;
import java.util.Map;
import java.util.function.Function;

public class Registry {
  private final Context context;

  public Registry(Context context) {
    this.context = context;
  }

  public Tool tool(String name, String description, Map<String, Object> parameters, Function<Map<String, Object>, Object> block) {
    Tool tool = new Tool(name, description, parameters, block);
    context.registerTool(tool);
    return tool;
  }

  /**
   * Whether a tool of this name is already registered. Lets a tool library skip
   * re-registering itself — registering twice would overwrite the tools while
   * leaking whatever resource the first set captured (an open MUD socket, say).
   */
  public boolean hasTool(String name) {
    return context.getTools().containsKey(name);
  }

  public Object dispatch(String name, Map<String, Object> args) {
    Tool tool = context.getTools().get(name);
    if (tool == null) {
      throw new UnknownToolError("No tool registered as '" + name + "'");
    }
    return tool.invoke(args);
  }
}
