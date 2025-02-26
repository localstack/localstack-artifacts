package cloud.localstack.auth;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.tinkerpop.gremlin.server.Settings;
import org.apache.tinkerpop.gremlin.server.auth.AuthenticatedUser;
import org.apache.tinkerpop.gremlin.server.auth.Authenticator;
import org.apache.tinkerpop.gremlin.server.authz.Authorizer;
import org.apache.tinkerpop.gremlin.server.handler.AbstractAuthenticationHandler;
import org.apache.tinkerpop.gremlin.server.handler.SaslAuthenticationHandler;
import org.apache.tinkerpop.gremlin.server.handler.StateKey;
import org.apache.tinkerpop.gremlin.util.Tokens;
import org.apache.tinkerpop.gremlin.util.message.RequestMessage;
import org.apache.tinkerpop.gremlin.util.message.ResponseMessage;
import org.apache.tinkerpop.gremlin.util.message.ResponseStatusCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.Attribute;

import cloud.localstack.LsStateKey;
import cloud.localstack.auth.HeaderAuthenticator.HeaderNegotiator;

/**
 * WebSocket authentication handler
 * When we receive an WS message this handler extract the credentials from the
 * header that was saved to
 * context by a post handshake handler
 */
@ChannelHandler.Sharable
public class WsIamAuthenticationHandler extends AbstractAuthenticationHandler {
    private static final Logger logger = LoggerFactory.getLogger(SaslAuthenticationHandler.class);
    protected final Settings settings;
    protected final HeaderAuthenticator authenticator;

    public WsIamAuthenticationHandler(Authenticator authenticator, Authorizer authorizer, Settings settings) {
        super(authenticator, authorizer);
        this.settings = settings;
        this.authenticator = (HeaderAuthenticator) authenticator;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        final Attribute<HeaderNegotiator> negotiator = ctx.channel().attr(LsStateKey.IAM_NEGOTIATOR);

        // This is a Websocket message
        final RequestMessage requestMessage = (RequestMessage) msg;

        if (negotiator.get() == null) {
            negotiator.set(authenticator.newHeaderNegotiator());
        }

        // if the negotiator is already complete, there is no need to get the user again
        if (!negotiator.get().isComplete()) {
            try {
                negotiator.get().evaluateHeader(ctx.channel().attr(LsStateKey.SIGNATURE_V4).get());

                AuthenticatedUser user = negotiator.get().getAuthenticatedUser();

                ctx.channel().attr(StateKey.AUTHENTICATED_USER).set(user);
            } catch (Exception ex) {
                respondWithError(requestMessage, builder -> builder.statusMessage("Forbidden")
                        .code(ResponseStatusCode.FORBIDDEN), ctx);
            }
        }

        ctx.fireChannelRead(msg);

    }

    private void respondWithError(final RequestMessage requestMessage,
            final Function<ResponseMessage.Builder, ResponseMessage.Builder> buildResponse,
            final ChannelHandlerContext ctx) {

        final Attribute<RequestMessage> originalRequest = ctx.channel().attr(StateKey.REQUEST_MESSAGE);
        final Attribute<Pair<LocalDateTime, List<RequestMessage>>> deferredRequests = ctx.channel()
                .attr(StateKey.DEFERRED_REQUEST_MESSAGES);

        if (!requestMessage.getOp().equals(Tokens.OPS_AUTHENTICATION)) {
            ctx.write(buildResponse.apply(ResponseMessage.build(requestMessage)).create());
        }

        if (originalRequest.get() != null) {
            ctx.write(buildResponse.apply(ResponseMessage.build(originalRequest.get())).create());
        }

        if (deferredRequests.get() != null) {
            deferredRequests
                    .getAndSet(null).getValue().stream()
                    .map(ResponseMessage::build)
                    .map(buildResponse)
                    .map(ResponseMessage.Builder::create)
                    .forEach(ctx::write);
        }
        ctx.flush();
    }
}
