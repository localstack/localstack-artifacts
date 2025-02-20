package cloud.localstack.auth;

import org.apache.tinkerpop.gremlin.server.Settings;
import org.apache.tinkerpop.gremlin.server.auth.Authenticator;
import org.apache.tinkerpop.gremlin.server.authz.Authorizer;
import org.apache.tinkerpop.gremlin.server.handler.AbstractAuthenticationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.HttpMessage;

import cloud.localstack.authz.HttpIamAuthorizationHandler;

import static io.netty.handler.codec.http.HttpHeaderNames.UPGRADE;
import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static org.apache.tinkerpop.gremlin.server.AbstractChannelizer.PIPELINE_AUTHENTICATOR;
import static org.apache.tinkerpop.gremlin.server.AbstractChannelizer.PIPELINE_AUTHORIZER;

/*
 * Main authentication handler. Since the pipeline order needs to be different when receiving a WS or HTTP
 * requests, we are using this handler to register the right handler depending on the request type.
 */
@ChannelHandler.Sharable
public class WsAndHttpAuthenticationHandler extends AbstractAuthenticationHandler {
    private static final Logger logger = LoggerFactory.getLogger(WsAndHttpAuthenticationHandler.class);
    private final AbstractAuthenticationHandler wsAuthenticationHandler;
    private final AbstractAuthenticationHandler httpAuthenticationHandler;

    final String LS_AUTH = "ls-authentication";

    public WsAndHttpAuthenticationHandler(Authenticator authenticator, Authorizer authorizer, Settings settings) {
        super(authenticator, authorizer);
        this.wsAuthenticationHandler = new WsIamAuthenticationHandler(authenticator, authorizer, settings);
        this.httpAuthenticationHandler = new HttpIamAuthenticationHandler(authenticator, authorizer, settings);
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        final ChannelPipeline pipeline = ctx.pipeline();
        logger.info("pipeline: {}", pipeline.names());

        if (msg instanceof FullHttpMessage && !isWebSocket((HttpMessage) msg)) {
            // Add http handlers
            if (pipeline.get(LS_AUTH) != null) {
                pipeline.remove(LS_AUTH);
            }
            // after this handler add the http handler
            pipeline.addAfter(PIPELINE_AUTHENTICATOR, LS_AUTH, httpAuthenticationHandler);

            if (authorizer != null) {
                final ChannelInboundHandlerAdapter authorizationHandler = new HttpIamAuthorizationHandler(authorizer);
                pipeline.remove(PIPELINE_AUTHORIZER);
                // Then add the authorizer handler
                pipeline.addAfter(LS_AUTH, PIPELINE_AUTHORIZER, authorizationHandler);
            }
            logger.info("pipeline: {}", pipeline.toMap().get("request-handler").getClass());

        } else {
            // add WS pipeline handlers
            if (pipeline.get(LS_AUTH) != null) {
                pipeline.remove(LS_AUTH);
            }
            pipeline.addAfter(PIPELINE_AUTHENTICATOR, LS_AUTH, wsAuthenticationHandler);
        }
        ctx.fireChannelRead(msg);
    }

    static boolean isWebSocket(final HttpMessage msg) {
        final String connectionHeader = msg.headers().get(CONNECTION);
        final String upgradeHeader = msg.headers().get(UPGRADE);
        return (null != connectionHeader && connectionHeader.equalsIgnoreCase("Upgrade")) ||
                (null != upgradeHeader && upgradeHeader.equalsIgnoreCase("WebSocket"));
    }
}
