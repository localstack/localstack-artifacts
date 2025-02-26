package cloud.localstack;

import org.apache.tinkerpop.gremlin.server.channel.WsAndHttpChannelizer;
import org.apache.tinkerpop.gremlin.server.util.ServerGremlinExecutor;

import cloud.localstack.auth.SigV4Handler;
import io.netty.channel.ChannelPipeline;

public class LocalStackChannelizer extends WsAndHttpChannelizer {

    @Override
    public void init(final ServerGremlinExecutor serverGremlinExecutor) {
        super.init(serverGremlinExecutor);
    }

    @Override
    public void configure(ChannelPipeline pipeline) {
        super.configure(pipeline);

        // Adding a handler to get the signature from the header
        if (this.authenticator != null) {
            pipeline.addBefore("ws-user-agent-handler", "ws-iam-sig-handler", new SigV4Handler());
        }
    }
}
