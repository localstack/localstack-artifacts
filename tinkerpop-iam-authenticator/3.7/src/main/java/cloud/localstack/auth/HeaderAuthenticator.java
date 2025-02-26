package cloud.localstack.auth;

import org.apache.tinkerpop.gremlin.server.auth.AuthenticatedUser;
import org.apache.tinkerpop.gremlin.server.auth.AuthenticationException;
import org.apache.tinkerpop.gremlin.server.auth.Authenticator;

public interface HeaderAuthenticator extends Authenticator {

    public HeaderNegotiator newHeaderNegotiator();

    public interface HeaderNegotiator {
        public void evaluateHeader(final String header) throws AuthenticationException;

        public boolean isComplete();

        public AuthenticatedUser getAuthenticatedUser() throws AuthenticationException;
    }
}
