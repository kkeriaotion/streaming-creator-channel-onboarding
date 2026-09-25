package learning.streaming.infrai;

import java.util.Map;

public interface OnboardingGateway {
    String createUser(String email, String password, String name, Map<String, Object> metadata, String idempotencyKey);
    boolean isEmailSuppressed(String email);
    boolean isSmsSuppressed(String phone);
    String sendEmail(String email, String subject, String text, String idempotencyKey);
    String sendSms(String phone, String body, String idempotencyKey);
}
