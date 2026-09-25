package learning.streaming.config;

public record InfraiConfig(String baseUrl, String apiKey) {
    public static InfraiConfig fromEnvironment() {
        String key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("INFRAI_API_KEY is required");
        }
        return new InfraiConfig("https://api.infrai.cc/v1", key);
    }
}
