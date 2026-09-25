package learning.streaming.infrai;

import learning.streaming.config.InfraiConfig;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public final class InfraiOnboardingClient implements OnboardingGateway {
    private final InfraiConfig config;
    private final HttpClient http;

    public InfraiOnboardingClient(InfraiConfig config) {
        this.config = config;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public String createUser(String email, String password, String name, Map<String, Object> metadata, String key) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", password);
        body.put("name", name);
        body.put("metadata", metadata);
        body.put("idempotency_key", key);
        return stringData(request("POST", "/auth/user/create", body, key), "user_id");
    }

    @Override
    public boolean isEmailSuppressed(String email) {
        Map<String, Object> data = request("GET", "/email/suppression/check/" + encode(email), null, null);
        return Boolean.TRUE.equals(data.get("suppressed"));
    }

    @Override
    public boolean isSmsSuppressed(String phone) {
        return Boolean.TRUE.equals(request("POST", "/sms/suppression/check", Map.of("phone", phone), null)
                .get("suppressed"));
    }

    @Override
    public String sendEmail(String email, String subject, String text, String key) {
        return stringData(request("POST", "/email/send",
                Map.of("to", email, "subject", subject, "body", text, "idempotency_key", key), key), "message_id");
    }

    @Override
    public String sendSms(String phone, String body, String key) {
        return stringData(request("POST", "/sms/send",
                Map.of("to", phone, "body", body, "idempotency_key", key), key), "message_id");
    }

    private Map<String, Object> request(String method, String path, Map<String, Object> body, String key) {
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(config.baseUrl() + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Accept", "application/json");
            if (key != null) builder.header("Idempotency-Key", key);
            if (body == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(Json.stringify(body)));
            }
            try {
                HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 429 && attempt < 3) {
                    pause(response, attempt);
                    continue;
                }
                Map<String, Object> envelope = Json.parseObject(response.body());
                if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                    Map<String, Object> error = object(envelope.get("error"));
                    throw new InfraiException(String.valueOf(error.getOrDefault("code", "REQUEST_REJECTED")), error,
                            response.statusCode());
                }
                if (response.statusCode() >= 500) throw new IllegalStateException("Infrai request failed");
                return object(envelope.get("data"));
            } catch (IOException e) {
                throw new IllegalStateException("Could not reach Infrai", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Infrai request interrupted", e);
            }
        }
        throw new IllegalStateException("Infrai rate limit retry budget exhausted");
    }

    private static void pause(HttpResponse<?> response, int attempt) throws InterruptedException {
        long fallback = 1L << attempt;
        String retryAfter = response.headers().firstValue("Retry-After").orElse("");
        long seconds;
        try { seconds = retryAfter.isBlank() ? fallback : Long.parseLong(retryAfter); }
        catch (NumberFormatException ignored) { seconds = fallback; }
        Thread.sleep(Math.min(seconds, 30) * 1000L);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String stringData(Map<String, Object> data, String field) {
        Object value = data.get(field);
        if (value == null) throw new IllegalStateException("Infrai response omitted " + field);
        return String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }
}
