package cloud.localstack.auth;

import org.apache.tinkerpop.gremlin.server.auth.AuthenticatedUser;
import org.apache.tinkerpop.gremlin.server.auth.AuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.Map;

/**
 * An authenticator that will enforce iam credentials usage for all gremlin
 * queries. If `enforce_iam` is not enabled, an anonymous user will be returned
 * to facilitate IAM_SOFT_MODE
 */
public class IamAuthenticator implements HeaderAuthenticator {
    private static final Logger logger = LoggerFactory.getLogger(IamAuthenticator.class);
    private static boolean enforceIam;

    @Override
    public boolean requireAuthentication() {
        return enforceIam;
    }

    @Override
    public void setup(final Map<String, Object> config) {
        logger.info("Initializing authentication with the {}", IamAuthenticator.class.getName());
        // wheater LS was started with `ENFORCE_IAM`
        enforceIam = Boolean.parseBoolean(config.get("EnforceIam").toString());
        logger.info("IAM strict mode: {}", enforceIam);
    }

    public AuthenticatedUser authenticate(final Map<String, String> credentials) throws AuthenticationException {
        return new AuthenticatedUser("username");
    }

    @Override
    public HeaderNegotiator newHeaderNegotiator() {
        return new Negotiator();
    }

    private static class Negotiator implements HeaderNegotiator {
        private boolean complete = false;
        private String userId = "";

        @Override
        public boolean isComplete() {
            return complete;
        }

        @Override
        public void evaluateHeader(String header) throws AuthenticationException {
            if (header == null) {
                header = "";
            }
            String credential = "";
            // TODO improve signature evaluation
            String[] sigParts = header.split(" ");
            for (String part : sigParts) {
                if (part.startsWith("Credential=")) {
                    credential = part.split("=")[1];
                }
            }

            userId = credential.split("/")[0];
            if (userId == "" && enforceIam) {
                logger.info("Failed to get account id from authorization signature");
                throw new AuthenticationException("Forbidden");
            }
            logger.debug("user found in credentials: {}", userId);
            complete = true;
        }

        @Override
        public AuthenticatedUser getAuthenticatedUser() throws AuthenticationException {
            if (!complete)
                throw new AuthenticationException("Header evaluation not complete");

            if (userId == "") {
                return AuthenticatedUser.ANONYMOUS_USER;
            }
            return new AuthenticatedUser(userId);
        }
    }

    @Override
    public SaslNegotiator newSaslNegotiator(InetAddress remoteAddress) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'newSaslNegotiator'");
    }
}