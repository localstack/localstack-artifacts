package cloud.localstack.authz;

import org.apache.tinkerpop.gremlin.server.auth.AuthenticatedUser;
import org.apache.tinkerpop.gremlin.server.authz.AuthorizationException;
import org.apache.tinkerpop.gremlin.server.authz.Authorizer;
import org.apache.tinkerpop.gremlin.server.handler.StateKey;
import org.apache.tinkerpop.gremlin.server.handler.WebSocketAuthorizationHandler;
import org.apache.tinkerpop.gremlin.util.message.RequestMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.FullHttpRequest;

import cloud.localstack.HttpHandlerUtil;

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;

/*
 * Handler that will get a gremlin query made over http and call the authorizer
 * to grant or deny the data access for the user
 */
@ChannelHandler.Sharable
public class HttpIamAuthorizationHandler extends ChannelInboundHandlerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketAuthorizationHandler.class);

    private final Authorizer authorizer;

    public HttpIamAuthorizationHandler(Authorizer authorizer) {
        this.authorizer = authorizer;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        if (msg instanceof FullHttpMessage) {
            final RequestMessage requestMessage;
            final FullHttpRequest httpRequest;

            try {
                httpRequest = (FullHttpRequest) msg;
                requestMessage = HttpHandlerUtil.getRequestMessageFromHttpRequest(httpRequest);
            } catch (IllegalArgumentException iae) {
                HttpHandlerUtil.sendError(ctx, BAD_REQUEST, iae.getMessage(), true);
                return;
            }
            try {
                AuthenticatedUser user = ctx.channel().attr(StateKey.AUTHENTICATED_USER).get();
                if (null == user) { // This is expected when using the AllowAllAuthenticator
                    user = AuthenticatedUser.ANONYMOUS_USER;
                }
                authorizer.authorize(user, requestMessage);
            } catch (AuthorizationException ae) {
                HttpHandlerUtil.sendError(ctx, FORBIDDEN, requestMessage.getRequestId(),
                        ae.getMessage(), true);
                ctx.close();
                return;
            }
            ctx.fireChannelRead(msg);
        } else {
            logger.warn("{} only processes FullHttpMessage instances - received {} - channel closing",
                    this.getClass().getSimpleName(), msg.getClass());
            ctx.close();
        }
    }
}
