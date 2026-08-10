package com.boukensha.api;

import com.boukensha.exception.ApiError;
import com.boukensha.model.PromptBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Client {
  private static final int[] RETRYABLE_STATUS_CODES = {408, 409, 429, 500, 502, 503, 504};
  private static final int MAX_RETRIES = 3;
  private static final double BASE_RETRY_DELAY = 0.5; // seconds
  private static final Set<Class<?>> TRANSIENT_ERRORS = new HashSet<>(Arrays.asList(
      SocketException.class,
      SocketTimeoutException.class,
      IOException.class
  ));

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

    int attempts = 0;
    Response response = null;

    while (true) {
      attempts++;

      try {
        String jsonBody = objectMapper.writeValueAsString(payload);
        RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json"));

        Request.Builder requestBuilder = new Request.Builder()
            .url(url)
            .post(body);
        for (Map.Entry<String, String> header : headers.entrySet()) {
          requestBuilder.addHeader(header.getKey(), header.getValue());
        }
        Request request = requestBuilder.build();

        response = httpClient.newCall(request).execute();
      } catch (Exception e) {
        if (attempts > MAX_RETRIES) {
          throw new ApiError("API request failed after " + attempts + " attempts: " + e.getClass().getName() + ": " + e.getMessage());
        }
        try {
          Thread.sleep((long) (retryDelay(attempts) * 1000));
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new ApiError("Interrupted during retry");
        }
        continue;
      }

      if (isRetryableStatus(response.code()) && attempts <= MAX_RETRIES) {
        try {
          Thread.sleep((long) (retryDelay(attempts) * 1000));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new ApiError("Interrupted during retry");
        }
        response.close();
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
