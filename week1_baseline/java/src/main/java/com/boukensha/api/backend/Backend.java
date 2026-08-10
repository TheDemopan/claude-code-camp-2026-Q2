package com.boukensha.api.backend;

import com.boukensha.exception.UnsupportedModelError;
import com.boukensha.model.Context;
import com.boukensha.tool.Tool;
import java.util.List;
import java.util.Map;

public abstract class Backend {
  protected String model;
  protected Map<String, Object> modelInfo;

  public String getModel() {
    return model;
  }

  public Map<String, Object> getModelInfo() {
    return modelInfo;
  }

  public abstract Map<String, Map<String, Object>> getModels();

  public abstract List<Map<String, Object>> toMessages(List<com.boukensha.model.Message> messages);

  public abstract List<Map<String, Object>> toTools(Map<String, Tool> tools);

  public abstract Map<String, Object> toPayload(Context context, Map<String, Object> opts);

  public abstract Map<String, Object> parseResponse(Map<String, Object> response);

  public abstract Map<String, String> getHeaders();

  public abstract String getUrl();

  protected void validateModel(String model) throws UnsupportedModelError {
    Map<String, Map<String, Object>> models = getModels();
    if (!models.containsKey(model)) {
      throw new UnsupportedModelError(
          this.getClass().getSimpleName() + " does not support model " + model +
          ". Supported models: " + String.join(", ", models.keySet()));
    }
    this.model = model;
    this.modelInfo = models.get(model);
  }

  protected Integer getMaxOutputTokens(Map<String, Object> opts) {
    Object val = opts.get("max_output_tokens");
    return val != null ? ((Number) val).intValue() : 1024;
  }

  @SuppressWarnings("unchecked")
  protected List<Map<String, Object>> getTools(Map<String, Object> opts) {
    Object val = opts.get("tools");
    return val != null ? (List<Map<String, Object>>) val : null;
  }
}
