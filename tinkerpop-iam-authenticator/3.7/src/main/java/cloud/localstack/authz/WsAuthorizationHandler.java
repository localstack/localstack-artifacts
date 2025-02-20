package cloud.localstack.authz;

import java.util.Map;

import org.apache.tinkerpop.gremlin.process.traversal.Bytecode;
import org.apache.tinkerpop.gremlin.server.auth.AuthenticatedUser;
import org.apache.tinkerpop.gremlin.server.authz.AuthorizationException;
import org.apache.tinkerpop.gremlin.server.authz.Authorizer;
import org.apache.tinkerpop.gremlin.server.handler.StateKey;
import org.apache.tinkerpop.gremlin.server.handler.WebSocketAuthorizationHandler;
import org.apache.tinkerpop.gremlin.util.Tokens;
import org.apache.tinkerpop.gremlin.util.message.RequestMessage;
import org.apache.tinkerpop.gremlin.util.message.ResponseMessage;
import org.apache.tinkerpop.gremlin.util.message.ResponseStatusCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/*
 * Handler that will get a gremlin query made over WS and call the authorizer
 * to grant or deny the data access for the user
 */
@ChannelHandler.Sharable
public class WsAuthorizationHandler extends ChannelInboundHandlerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketAuthorizationHandler.class);

    private AuthenticatedUser user;
    private final Authorizer authorizer;

    public WsAuthorizationHandler(Authorizer authorizer) {
        this.authorizer = authorizer;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        if (msg instanceof RequestMessage) {
            final RequestMessage requestMessage = (RequestMessage) msg;
            try {
                user = ctx.channel().attr(StateKey.AUTHENTICATED_USER).get();
                if (null == user) { // This is expected when using the AllowAllAuthenticator
                    user = AuthenticatedUser.ANONYMOUS_USER;
                }
                switch (requestMessage.getOp()) {
                    case Tokens.OPS_BYTECODE:
                        final Bytecode bytecode = (Bytecode) requestMessage.getArgs().get(Tokens.ARGS_GREMLIN);
                        final Map<String, String> aliases = (Map<String, String>) requestMessage.getArgs()
                                .get(Tokens.ARGS_ALIASES);
                        authorizer.authorize(user, bytecode, aliases);
                        ctx.fireChannelRead(requestMessage);
                        break;
                    case Tokens.OPS_EVAL:
                        authorizer.authorize(user, requestMessage);
                        ctx.fireChannelRead(requestMessage);
                        break;
                    default:
                        throw new AuthorizationException(
                                "This AuthorizationHandler only handles requests with OPS_BYTECODE or OPS_EVAL.");
                }
            } catch (AuthorizationException ex) { // Expected: users can alternate between allowed and disallowed
                                                  // requests
                String address = ctx.channel().remoteAddress().toString();
                if (address.startsWith("/") && address.length() > 1)
                    address = address.substring(1);
                interruptEvaluation(ctx, requestMessage, ex.getMessage());
            } catch (Exception ex) {
                logger.error("{} is not ready to handle requests - unknown error",
                        authorizer.getClass().getSimpleName());
                interruptEvaluation(ctx, requestMessage, "Unknown error in gremlin-server");
            }
        } else {
            logger.warn("{} only processes RequestMessage instances - received {} - channel closing",
                    this.getClass().getSimpleName(), msg.getClass());
            ctx.close();
        }
    }

    private void interruptEvaluation(final ChannelHandlerContext ctx, final RequestMessage requestMessage,
            final String errorMessage) {
        // Modiying the response as aws return a 403 while the default dehavior of
        // gremlin is 401
        final ResponseMessage error = ResponseMessage.build(requestMessage)
                .statusMessage(errorMessage)
                .code(ResponseStatusCode.FORBIDDEN).create();
        ctx.writeAndFlush(error);
    }
}
