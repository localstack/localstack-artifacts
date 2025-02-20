package cloud.localstack.auth;

import org.apache.tinkerpop.gremlin.server.Settings;
import org.apache.tinkerpop.gremlin.server.auth.AuthenticatedUser;
import org.apache.tinkerpop.gremlin.server.auth.Authenticator;
import org.apache.tinkerpop.gremlin.server.authz.Authorizer;
import org.apache.tinkerpop.gremlin.server.handler.AbstractAuthenticationHandler;
import org.apache.tinkerpop.gremlin.server.handler.SaslAuthenticationHandler;
import org.apache.tinkerpop.gremlin.server.handler.StateKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.util.Attribute;
import io.netty.util.ReferenceCountUtil;

import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;

import cloud.localstack.LsStateKey;
import cloud.localstack.auth.HeaderAuthenticator.HeaderNegotiator;

/**
 * Http authentication handler
 * When we receive an http request this handler extract the credentials from the
 * headers and registers the user
 */
@ChannelHandler.Sharable
public class HttpIamAuthenticationHandler extends AbstractAuthenticationHandler {
    private static final Logger logger = LoggerFactory.getLogger(SaslAuthenticationHandler.class);
    protected final Settings settings;
    protected final HeaderAuthenticator authenticator;

    public HttpIamAuthenticationHandler(Authenticator authenticator, Authorizer authorizer, Settings settings) {
        super(authenticator, authorizer);
        this.settings = settings;
        this.authenticator = (HeaderAuthenticator) authenticator;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        final Attribute<HeaderNegotiator> negotiator = ctx.channel().attr(LsStateKey.IAM_NEGOTIATOR);

        if (negotiator.get() == null) {
            negotiator.set(authenticator.newHeaderNegotiator());
        }

        // we analyze the request to find the authorization header and set the user
        final FullHttpMessage request = (FullHttpMessage) msg;
        final String authorizationHeader = request.headers().get("Authorization");

        if (!negotiator.get().isComplete()) {
            try {
                negotiator.get().evaluateHeader(authorizationHeader);
                AuthenticatedUser user = negotiator.get().getAuthenticatedUser();
                ctx.channel().attr(StateKey.AUTHENTICATED_USER).set(user);
            } catch (Exception ex) {
                // TODO validate error message returned by aws
                sendHttpError(ctx, msg);
                return;
            }
        }
        ctx.fireChannelRead(msg);
    }

    private void sendHttpError(final ChannelHandlerContext ctx, final Object msg) {
        // Close the connection as soon as the error message is sent.
        ctx.writeAndFlush(new DefaultFullHttpResponse(HTTP_1_1,
                FORBIDDEN)).addListener(ChannelFutureListener.CLOSE);
        ReferenceCountUtil.release(msg);
    }

}
