package learning.streaming.web;

import learning.streaming.config.InfraiConfig;
import learning.streaming.domain.CreatorOnboarding;
import learning.streaming.infrai.InfraiException;
import learning.streaming.infrai.InfraiOnboardingClient;
import learning.streaming.infrai.Json;
import learning.streaming.service.CreatorWelcomeService;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

public final class CreatorOnboardingServer {
    private CreatorOnboardingServer() {}

    public static void main(String[] args) throws IOException {
        CreatorWelcomeService service = new CreatorWelcomeService(
                new InfraiOnboardingClient(InfraiConfig.fromEnvironment()));
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/creators/onboard", exchange -> handle(exchange, service));
        server.start();
        System.out.println("Creator onboarding listening on http://localhost:" + port);
    }

    private static void handle(com.sun.net.httpserver.HttpExchange exchange, CreatorWelcomeService service) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            write(exchange, 405, Map.of("error", "method_not_allowed"));
            return;
        }
        try {
            Map<String, Object> input = Json.parseObject(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            CreatorOnboarding.Request request = mapRequest(input);
            CreatorOnboarding.Result result = service.onboard(request);
            write(exchange, 201, Map.of("user_id", result.userId(),
                    "delivered_by", result.deliveredBy().name().toLowerCase(), "message_id", result.messageId()));
        } catch (CreatorWelcomeService.NoDeliveryChannelException e) {
            write(exchange, 409, Map.of("error", e.getMessage()));
        } catch (InfraiException e) {
            int status = e.status() >= 400 && e.status() < 500 ? e.status() : 502;
            write(exchange, status, Map.of("error", e.code(), "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            write(exchange, 400, Map.of("error", "invalid_request", "message", e.getMessage()));
        }
    }

    private static CreatorOnboarding.Request mapRequest(Map<String, Object> input) {
        String course = required(input, "course_title");
        return new CreatorOnboarding.Request(
                required(input, "email"), required(input, "phone"), required(input, "password"),
                required(input, "name"), CreatorOnboarding.SignupChannel.valueOf(required(input, "signup_channel").toUpperCase()),
                new CreatorOnboarding.AssetIngestion(required(input, "asset_id"), required(input, "asset_source")),
                new CreatorOnboarding.ProcessingJob(required(input, "processing_job_id"), required(input, "processing_state")),
                new CreatorOnboarding.CreatorDelivery(required(input, "creator_id"), course),
                String.valueOf(input.getOrDefault("idempotency_key", UUID.randomUUID().toString())));
    }

    private static String required(Map<String, Object> input, String key) {
        Object value = input.get(key);
        if (value == null || String.valueOf(value).isBlank()) throw new IllegalArgumentException(key + " is required");
        return String.valueOf(value);
    }

    private static void write(com.sun.net.httpserver.HttpExchange exchange, int status, Map<String, Object> body) throws IOException {
        byte[] bytes = Json.stringify(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
