package learning.streaming.service;

import learning.streaming.domain.CreatorOnboarding;
import learning.streaming.infrai.OnboardingGateway;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CreatorWelcomeServiceTest {
    public static void main(String[] args) {
        FakeGateway gateway = new FakeGateway();
        CreatorWelcomeService service = new CreatorWelcomeService(gateway);
        CreatorOnboarding.Request input = new CreatorOnboarding.Request(
                "teacher@example.edu", "+15550102030", "correct-horse-library", "Ari Teacher",
                CreatorOnboarding.SignupChannel.EMAIL,
                new CreatorOnboarding.AssetIngestion("asset-42", "lesson-camera.mp4"),
                new CreatorOnboarding.ProcessingJob("job-9", "ready"),
                new CreatorOnboarding.CreatorDelivery("creator-7", "Editing Your First Lesson"),
                "onboard-creator-7");

        CreatorOnboarding.Result result = service.onboard(input);

        check(result.deliveredBy() == CreatorOnboarding.SignupChannel.SMS, "suppressed email should fall back to SMS");
        check(result.userId().equals("user-7"), "account id should flow into the result");
        check(gateway.calls.equals(List.of("create", "check-email", "check-sms", "send-sms")),
                "decision should make only the required calls: " + gateway.calls);
        check(gateway.lastSms.contains("user-7") && gateway.lastSms.contains("job-9"),
                "delivery should receive account and processing data directly");
        System.out.println("PASS email suppression selects SMS and carries the account handoff");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class FakeGateway implements OnboardingGateway {
        private final List<String> calls = new ArrayList<>();
        private String lastSms;

        public String createUser(String email, String password, String name, Map<String, Object> metadata, String key) {
            calls.add("create"); return "user-7";
        }
        public boolean isEmailSuppressed(String email) { calls.add("check-email"); return true; }
        public boolean isSmsSuppressed(String phone) { calls.add("check-sms"); return false; }
        public String sendEmail(String email, String subject, String text, String key) {
            calls.add("send-email"); return "mail-1";
        }
        public String sendSms(String phone, String body, String key) {
            calls.add("send-sms"); lastSms = body; return "sms-1";
        }
    }
}
