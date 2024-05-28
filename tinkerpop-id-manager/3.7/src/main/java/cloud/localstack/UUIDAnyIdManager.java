package cloud.localstack;


import org.apache.tinkerpop.gremlin.tinkergraph.structure.AbstractTinkerGraph;

import java.util.UUID;

public class UUIDAnyIdManager implements AbstractTinkerGraph.IdManager<String> {
    @Override
    public String getNextId(final AbstractTinkerGraph graph) {
        return UUID.randomUUID().toString();
    }

    @Override
    public String convert(final Object id) {
        if (null == id) {
            return null;
        }
        if (!(id instanceof String)) {
            throw new IllegalArgumentException(String.format("Expected an id that is String but received %s - [%s]", id.getClass(), id));
        }
        return (String) id;
    }

    @Override
    public boolean allow(final Object id) {
        return id instanceof String;
    }
}
