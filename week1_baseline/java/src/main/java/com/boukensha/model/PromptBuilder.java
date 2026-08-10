package com.boukensha.model;

import com.boukensha.api.backend.Backend;
import java.util.List;
import java.util.Map;

public class PromptBuilder {
  private final Context context;
  private final Backend backend;

  public PromptBuilder(Context context, Backend backend) {
    this.context = context;
    this.backend = backend;
  }

  public List<Map<String, Object>> toMessages() {
    return backend.toMessages(context.getMessages());
  }

  public List<Map<String, Object>> toTools() {
    return backend.toTools(context.getTools());
  }

  public Map<String, Object> toApiPayload(Map<String, Object> opts) {
    return backend.toPayload(context, opts);
  }

  public Map<String, Object> parseResponse(Map<String, Object> response) {
    return backend.parseResponse(response);
  }

  public Map<String, String> getHeaders() {
    return backend.getHeaders();
  }

  public String getUrl() {
    return backend.getUrl();
  }
}
