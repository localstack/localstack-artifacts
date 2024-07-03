//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package org.apache.tinkerpop.gremlin.process.traversal.step.util;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import org.apache.tinkerpop.gremlin.process.traversal.Compare;
import org.apache.tinkerpop.gremlin.process.traversal.Contains;
import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.util.CloseableIterator;
import org.apache.tinkerpop.gremlin.util.iterator.IteratorUtils;

public class HasContainer implements Serializable, Cloneable, Predicate<Element> {

    public static final String LABEL_DELIMITER = "::";

    private String key;
    private P predicate;
    private final boolean testingIdString;

    public HasContainer(final String key, final P<?> predicate) {
        this.key = key;
        this.predicate = predicate;
        if (!this.key.equals(T.id.getAccessor())) {
            this.testingIdString = false;
        } else {
            Object predicateValue = this.predicate.getValue();
            this.enforceHomogenousCollectionIfPresent(predicateValue);
            Object valueInstance = this.predicate.getValue() instanceof Collection ? (((Collection)this.predicate.getValue()).isEmpty() ? new Object() : ((Collection)this.predicate.getValue()).toArray()[0]) : this.predicate.getValue();
            this.testingIdString = this.key.equals(T.id.getAccessor()) && valueInstance instanceof String;
            if (this.testingIdString) {
                this.predicate.setValue(this.predicate.getValue() instanceof Collection ? IteratorUtils.set(IteratorUtils.map(((Collection)this.predicate.getValue()).iterator(), Object::toString)) : this.predicate.getValue().toString());
            }
        }

    }

    public final boolean test(final Element element) {
        if (this.key.equals(T.id.getAccessor())) {
            return this.testingIdString ? this.testIdAsString(element) : this.testId(element);
        } else if (this.key.equals(T.label.getAccessor())) {
            return this.testLabel(element);
        } else {
            Iterator<? extends Property> itty = element.properties(new String[]{this.key});

            try {
                while(itty.hasNext()) {
                    if (this.testValue((Property)itty.next())) {
                        boolean var3 = true;
                        return var3;
                    }
                }
            } finally {
                CloseableIterator.closeIterator(itty);
            }

            return false;
        }
    }

    public final boolean test(final Property property) {
        if (this.key.equals(T.value.getAccessor())) {
            return this.testValue(property);
        } else if (this.key.equals(T.key.getAccessor())) {
            return this.testKey(property);
        } else {
            return property instanceof Element ? this.test((Element)property) : false;
        }
    }

    protected boolean testId(Element element) {
        return this.predicate.test(element.id());
    }

    protected boolean testIdAsString(Element element) {
        return this.predicate.test(element.id().toString());
    }

    protected boolean testLabel(Element element) {

        /* start patch for localstack */

        // add comparison predicates to support the multi-label syntax `label1::label2::label3`

        class LabelEquals implements BiPredicate<String, String> {
            public boolean test(String label, String otherLabel) {
                if (label == null) return false;
                for (String partialLabel: label.split(LABEL_DELIMITER)) {
                    if (partialLabel.equals(otherLabel)) {
                        return true;
                    }
                }
                return false;
            }
        }

        class LabelWithin implements BiPredicate<String, List<String>> {
            public boolean test(String label, List<String> labels) {
                if (label == null) return false;
                for (String partialLabel: label.split(LABEL_DELIMITER)) {
                    if (labels.contains(partialLabel)) {
                        return true;
                    }
                }
                return false;
            }
        }

        if (this.predicate.getBiPredicate() == Compare.eq && this.predicate.getValue() instanceof String) {
            this.predicate = new P(new LabelEquals(), this.predicate.getValue());
        } else if (this.predicate.getBiPredicate() == Contains.within){
            if (this.predicate.getValue() instanceof String[]) {
                // this should catch all hasLabel('LabelX') requests
                final List<String> labelsList = Arrays.asList((String[])this.predicate.getValue());
                this.predicate = new P(new LabelWithin(), labelsList);
            } else if (this.predicate.getValue() instanceof List){
                // this should handle the traverse logic for hasLabel('LabelX', 'LabelXYZ') e.g. any match from that list
                boolean validatedAllStrings = true;
                for (Object s : (List) this.predicate.getValue()){
                    // not sure if we can rely on having only Strings here
                    // verify the content before casting
                    if(! (s instanceof String)){
                        validatedAllStrings = false;
                        break;
                    }
                }
                if (validatedAllStrings) this.predicate = new P(new LabelWithin(), this.predicate.getValue());
            }
        }

        /* end patch for localstack */
        return this.predicate.test(element.label());
    }

    protected boolean testValue(Property property) {
        return this.predicate.test(property.value());
    }

    protected boolean testKey(Property property) {
        return this.predicate.test(property.key());
    }

    public final String toString() {
        return this.key + '.' + this.predicate;
    }

    public HasContainer clone() {
        try {
            HasContainer clone = (HasContainer)super.clone();
            clone.predicate = this.predicate.clone();
            return clone;
        } catch (CloneNotSupportedException var2) {
            CloneNotSupportedException e = var2;
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    public int hashCode() {
        return (this.key != null ? this.key.hashCode() : 0) ^ (this.predicate != null ? this.predicate.hashCode() : 0);
    }

    public final String getKey() {
        return this.key;
    }

    public final void setKey(final String key) {
        this.key = key;
    }

    public final P<?> getPredicate() {
        return this.predicate;
    }

    public final BiPredicate<?, ?> getBiPredicate() {
        return this.predicate.getBiPredicate();
    }

    public final Object getValue() {
        return this.predicate.getValue();
    }

    private void enforceHomogenousCollectionIfPresent(final Object predicateValue) {
        if (predicateValue instanceof Collection) {
            Collection collection = (Collection)predicateValue;
            if (!collection.isEmpty()) {
                Class<?> first = collection.toArray()[0].getClass();
                if (!((Collection)predicateValue).stream().map(Object::getClass).allMatch((c) -> {
                    return first.equals(c);
                })) {
                    throw new IllegalArgumentException("Has comparisons on a collection of ids require ids to all be of the same type");
                }
            }
        }

    }

    public static <V> boolean testAll(final Property<V> property, final List<HasContainer> hasContainers) {
        return internalTestAll(property, hasContainers);
    }

    public static boolean testAll(final Element element, final List<HasContainer> hasContainers) {
        return internalTestAll(element, hasContainers);
    }

    private static <S> boolean internalTestAll(final S element, final List<HasContainer> hasContainers) {
        boolean isProperty = element instanceof Property;
        Iterator var3 = hasContainers.iterator();

        while(var3.hasNext()) {
            HasContainer hasContainer = (HasContainer)var3.next();
            if (isProperty) {
                if (!hasContainer.test((Property)element)) {
                    return false;
                }
            } else if (!hasContainer.test((Element)element)) {
                return false;
            }
        }

        return true;
    }
}
