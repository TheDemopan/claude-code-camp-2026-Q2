package com.boukensha.api;

import com.boukensha.exception.ApiError;
import com.boukensha.model.PromptBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.io.IOException;
import java.util.Map;

public class Client {
  private static final int[] RETRYABLE_STATUS_CODES = {408, 409, 429, 500, 502, 503, 504};
  private static final int MAX_RETRIES = 3;
  private static final double BASE_RETRY_DELAY = 0.5; // seconds

  private final PromptBuilder builder;
  private final OkHttpClient httpClient;
  private final ObjectMapper objectMapper;

  public Client(PromptBuilder builder) {
    this.builder = builder;
    this.httpClient = new OkHttpClient();
    this.objectMapper = new ObjectMapper();
  }

  public Map<String, Object> call(Map<String, Object> opts) {
    String url = builder.getUrl();
    Map<String, String> headers = builder.getHeaders();
    Map<String, Object> payload = builder.toApiPayload(opts);

    // Built once, outside the retry loop: serializing the payload is
    // deterministic, so a failure here is a payload bug that retrying cannot fix.
    Request request = buildRequest(url, headers, payload);

    int attempts = 0;
    Response response = null;

    while (true) {
      attempts++;

      try {
        response = httpClient.newCall(request).execute();
      } catch (IOException e) {
        // Transient network failure: timeout, connection reset, SSL error.
        if (attempts > MAX_RETRIES) {
          throw new ApiError("API request failed after " + attempts + " attempts: " + e.getClass().getName() + ": " + e.getMessage());
        }
        sleepBeforeRetry(attempts);
        continue;
      }

      if (isRetryableStatus(response.code()) && attempts <= MAX_RETRIES) {
        response.close();
        sleepBeforeRetry(attempts);
        continue;
      }

      break;
    }

    if (response == null || !response.isSuccessful()) {
      String body = "";
      try {
        if (response != null && response.body() != null) {
          body = response.body().string();
        }
      } catch (IOException e) {
        // Ignore
      }
      throw new ApiError("API request failed after " + attempts + " attempt" + (attempts == 1 ? "" : "s") +
                         " (" + (response != null ? response.code() : "unknown") + "): " + body);
    }

    try {
      String bodyStr = response.body().string();
      return objectMapper.readValue(bodyStr, Map.class);
    } catch (IOException e) {
      throw new ApiError("Failed to parse API response: " + e.getMessage());
    } finally {
      response.close();
    }
  }

  private Request buildRequest(String url, Map<String, String> headers, Map<String, Object> payload) {
    String jsonBody;
    try {
      jsonBody = objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      throw new ApiError("Failed to serialize request payload: " + e.getMessage(), e);
    }

    RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json"));
    Request.Builder requestBuilder = new Request.Builder().url(url).post(body);
    for (Map.Entry<String, String> header : headers.entrySet()) {
      requestBuilder.addHeader(header.getKey(), header.getValue());
    }
    return requestBuilder.build();
  }

  private void sleepBeforeRetry(int attempt) {
    try {
      Thread.sleep((long) (retryDelay(attempt) * 1000));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ApiError("Interrupted during retry");
    }
  }

  private boolean isRetryableStatus(int code) {
    for (int retryCode : RETRYABLE_STATUS_CODES) {
      if (code == retryCode) {
        return true;
      }
    }
    return false;
  }

  private double retryDelay(int attempt) {
    return BASE_RETRY_DELAY * Math.pow(2, attempt - 1);
  }
}
