package cloud.localstack;

import org.apache.tinkerpop.gremlin.server.channel.WsAndHttpChannelizer;
import org.apache.tinkerpop.gremlin.server.util.ServerGremlinExecutor;

import cloud.localstack.auth.SigV4Handler;
import cloud.localstack.authz.WsAuthorizationHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;

public class LocalStackChannelizer extends WsAndHttpChannelizer {

    private ChannelInboundHandlerAdapter authorizationHandler;

    @Override
    public void init(final ServerGremlinExecutor serverGremlinExecutor) {
        super.init(serverGremlinExecutor);

        if (authorizer != null) {
            authorizationHandler = new WsAuthorizationHandler(authorizer);
        }
    }

    @Override
    public void configure(ChannelPipeline pipeline) {
        super.configure(pipeline);

        // Adding a handler to get the signature from the header
        pipeline.addBefore("ws-user-agent-handler", "ws-iam-sig-handler", new SigV4Handler());

        // Replacing the authorizer handler with our own, to allow for a better control
        // over the error message and status code
        if (authorizationHandler != null)
            pipeline.replace(PIPELINE_AUTHORIZER, PIPELINE_AUTHORIZER, authorizationHandler);
    }
}
