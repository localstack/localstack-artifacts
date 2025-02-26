package cloud.localstack.auth;

import org.apache.tinkerpop.gremlin.server.handler.WsUserAgentHandler;

import cloud.localstack.LsStateKey;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;

/*
 * Handler that will be processed after the WebSocket handshake
 * Aws seems to raise earlier if the signature is invalid, but more experimentation
 * will be required to narrow it down
 */
public class SigV4Handler extends WsUserAgentHandler {
    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx, java.lang.Object evt) {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            final HttpHeaders requestHeaders = ((WebSocketServerProtocolHandler.HandshakeComplete) evt)
                    .requestHeaders();
            /**
             * aws does not complete the handshake when the authorization header is missing,
             * but since we want to support `IAM_SOFT_MODE` also, it should do for now to
             * get the headers after the handshake
             * and raise in the Authenticator.
             */
            String authorizationHeader = requestHeaders.get("Authorization");

            // Adding the authorization header to the context to be reused in the
            // authenticator
            ctx.channel().attr(LsStateKey.SIGNATURE_V4).set(authorizationHeader);
        }
        ctx.fireUserEventTriggered(evt);
    }
}
