//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package org.apache.tinkerpop.gremlin.process.traversal.step.util;

import java.io.Serializable;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import org.apache.tinkerpop.gremlin.process.traversal.Compare;
import org.apache.tinkerpop.gremlin.process.traversal.Contains;
import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.PBiPredicate;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.util.CloseableIterator;


public class HasContainer implements Serializable, Cloneable, Predicate<Element> {

    public static final String LABEL_DELIMITER = "::";

    private String key;
    private P predicate;
    private final boolean testingIdString;

    public HasContainer(final String key, final P<?> predicate) {
        this.key = key;
        this.predicate = predicate;
        this.testingIdString = this.isStringTestable();
    }

    public final boolean test(final Element element) {
        if (this.key != null) {
            if (this.key.equals(T.id.getAccessor())) {
                return this.testingIdString ? this.testIdAsString(element) : this.testId(element);
            }
            if (this.key.equals(T.label.getAccessor())) {
                return this.testLabel(element);
            }
        }

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

    public final boolean test(final Property property) {
        if (this.key != null) {
            if (this.key.equals(T.value.getAccessor())) {
                return this.testValue(property);
            }

            if (this.key.equals(T.key.getAccessor())) {
                return this.testKey(property);
            }
        }

        return property instanceof Element ? this.test((Element)property) : false;
    }

    protected boolean testId(final Element element) {
        return this.predicate.test(element.id());
    }

    protected boolean testIdAsString(final Element element) {
        return this.predicate.test(element.id().toString());
    }

     protected boolean testLabel(final Element element) {

        /* start patch for localstack */

        // add comparison predicates to support the multi-label syntax `label1::label2::label3`

        class LabelEquals implements PBiPredicate<String, String> {
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

        class LabelWithin implements PBiPredicate<String, List<String>> {
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

    protected boolean testValue(final Property property) {
        return this.predicate.test(property.value());
    }

    protected boolean testKey(final Property property) {
        return this.predicate.test(property.key());
    }

    public final String toString() {
        return Objects.toString(this.key) + '.' + this.predicate;
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

    private boolean isStringTestable() {
        if (this.key != null && this.key.equals(T.id.getAccessor())) {
            Object predicateValue = null == this.predicate ? null : this.predicate.getValue();
            if (predicateValue instanceof Collection) {
                Collection collection = (Collection)predicateValue;
                if (!collection.isEmpty()) {
                    return ((Collection)predicateValue).stream().allMatch((c) -> {
                        return null == c || c instanceof String;
                    });
                }
            }

            return predicateValue instanceof String;
        } else {
            return false;
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
