package learning.streaming.domain;

import java.util.Map;

public final class CreatorOnboarding {
    private CreatorOnboarding() {}

    public enum SignupChannel { EMAIL, SMS }

    public record AssetIngestion(String assetId, String sourceName) {}

    public record ProcessingJob(String jobId, String state) {}

    public record CreatorDelivery(String creatorId, String courseTitle) {}

    public record Request(
            String email,
            String phone,
            String password,
            String name,
            SignupChannel signupChannel,
            AssetIngestion ingestion,
            ProcessingJob processing,
            CreatorDelivery delivery,
            String idempotencyKey) {
        public Map<String, Object> accountMetadata() {
            return Map.of(
                    "asset_id", ingestion.assetId(),
                    "asset_source", ingestion.sourceName(),
                    "processing_job_id", processing.jobId(),
                    "processing_state", processing.state(),
                    "creator_id", delivery.creatorId(),
                    "course_title", delivery.courseTitle());
        }
    }

    public record Result(String userId, SignupChannel deliveredBy, String messageId) {}
}
