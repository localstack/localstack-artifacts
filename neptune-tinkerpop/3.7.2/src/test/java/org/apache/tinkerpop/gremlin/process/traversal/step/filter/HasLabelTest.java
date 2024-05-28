package org.apache.tinkerpop.gremlin.process.traversal.step.filter;

import org.apache.tinkerpop.gremlin.LoadGraphWith;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.apache.tinkerpop.gremlin.LoadGraphWith.GraphData.MODERN;

import org.apache.tinkerpop.gremlin.AbstractGremlinTest;
import org.apache.tinkerpop.gremlin.GraphManager;
import org.apache.tinkerpop.gremlin.tinkergraph.TinkerGraphProvider;

public class HasLabelTest extends AbstractGremlinTest
{
    public HasLabelTest() {
        GraphManager.setGraphProvider(new TinkerGraphProvider());
    }

    @Test
    @LoadGraphWith(MODERN)
    public void testHasLabelMultiValue() {
        final String label1 = "l" + Math.random();
        final String label2 = "l" + Math.random();
        final String label3 = "l" + Math.random();

        // add a couple of vertices with single labels and multi-labels
        g.addV(label1).iterate();
        g.addV(label2).iterate();
        g.addV(label3).iterate();
        g.addV(label1 + "::" + label2).iterate();
        g.addV(label1 + "::" + label2 + "::" + label1).iterate();

        // assert that vertices can be selected via single sub-labels
        assertEquals(g.V().hasLabel(label1).toList().size(), 3);
        assertEquals(g.V().hasLabel(label1).hasLabel(label2).toList().size(), 2);

        // assert that vertices can be selected via a set of alternative sub-labels
        assertEquals(g.V().hasLabel(label1, label2).toList().size(), 4);
        assertEquals(g.V().hasLabel(label1, label2, label3).toList().size(), 5);
    }
}
