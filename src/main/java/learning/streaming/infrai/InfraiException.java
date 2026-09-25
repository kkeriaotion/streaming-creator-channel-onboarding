package learning.streaming.infrai;

import java.util.Map;

public final class InfraiException extends RuntimeException {
    private final String code;
    private final int status;

    public InfraiException(String code, Map<String, Object> error, int status) {
        super(String.valueOf(error.getOrDefault("message", code)));
        this.code = code;
        this.status = status;
    }

    public String code() { return code; }
    public int status() { return status; }
}
