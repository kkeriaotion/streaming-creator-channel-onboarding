package learning.streaming.service;

import learning.streaming.domain.CreatorOnboarding;
import learning.streaming.domain.CreatorOnboarding.SignupChannel;
import learning.streaming.infrai.OnboardingGateway;

public final class CreatorWelcomeService {
    private final OnboardingGateway gateway;

    public CreatorWelcomeService(OnboardingGateway gateway) { this.gateway = gateway; }

    public CreatorOnboarding.Result onboard(CreatorOnboarding.Request request) {
        String userId = gateway.createUser(request.email(), request.password(), request.name(),
                request.accountMetadata(), request.idempotencyKey() + "-account");
        String lesson = "Your course '" + request.delivery().courseTitle() + "' is ready after processing job "
                + request.processing().jobId() + ". Account: " + userId;

        if (request.signupChannel() == SignupChannel.EMAIL) {
            if (!gateway.isEmailSuppressed(request.email())) {
                return delivered(userId, SignupChannel.EMAIL,
                        gateway.sendEmail(request.email(), "Your creator workspace is ready", lesson,
                                request.idempotencyKey() + "-email"));
            }
            ensureSmsAvailable(request.phone());
            return delivered(userId, SignupChannel.SMS,
                    gateway.sendSms(request.phone(), lesson, request.idempotencyKey() + "-sms"));
        }

        if (!gateway.isSmsSuppressed(request.phone())) {
            return delivered(userId, SignupChannel.SMS,
                    gateway.sendSms(request.phone(), lesson, request.idempotencyKey() + "-sms"));
        }
        ensureEmailAvailable(request.email());
        return delivered(userId, SignupChannel.EMAIL,
                gateway.sendEmail(request.email(), "Your creator workspace is ready", lesson,
                        request.idempotencyKey() + "-email"));
    }

    private void ensureSmsAvailable(String phone) {
        if (gateway.isSmsSuppressed(phone)) throw new NoDeliveryChannelException();
    }

    private void ensureEmailAvailable(String email) {
        if (gateway.isEmailSuppressed(email)) throw new NoDeliveryChannelException();
    }

    private static CreatorOnboarding.Result delivered(String userId, SignupChannel channel, String messageId) {
        return new CreatorOnboarding.Result(userId, channel, messageId);
    }

    public static final class NoDeliveryChannelException extends RuntimeException {
        public NoDeliveryChannelException() { super("The creator has no permitted welcome channel"); }
    }
}
