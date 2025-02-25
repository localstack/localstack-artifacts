package cloud.localstack;

import cloud.localstack.auth.HeaderAuthenticator.HeaderNegotiator;
import io.netty.util.AttributeKey;

public final class LsStateKey {
    private LsStateKey() {
    }

    /**
     * The key for the requests authorization header
     */
    public static final AttributeKey<String> SIGNATURE_V4 = AttributeKey.valueOf("signatureV4");
    public static final AttributeKey<HeaderNegotiator> IAM_NEGOTIATOR = AttributeKey.valueOf("iamNegotiator");
}
