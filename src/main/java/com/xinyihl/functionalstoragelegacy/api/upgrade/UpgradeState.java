package com.xinyihl.functionalstoragelegacy.api.upgrade;

import java.util.*;

/**
 * Immutable snapshot of all contributions made by installed storage upgrades.
 *
 * <p>The snapshot owns copies of all builder collections. Returned maps, lists, and sets are
 * unmodifiable and remain stable if the originating builder is reused. Instances are therefore
 * thread-safe after construction; {@link Builder} itself is mutable and not thread-safe.</p>
 */
public final class UpgradeState {

    private static final UpgradeState EMPTY = new Builder().build();

    private final Map<UpgradeAttribute, List<UpgradeModifier>> modifiers;
    private final Set<StorageFeature> features;

    private UpgradeState(Builder builder) {
        EnumMap<UpgradeAttribute, List<UpgradeModifier>> modifierCopies = new EnumMap<>(UpgradeAttribute.class);
        for (Map.Entry<UpgradeAttribute, List<UpgradeModifier>> entry : builder.modifiers.entrySet()) {
            modifierCopies.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        }
        modifiers = Collections.unmodifiableMap(modifierCopies);
        features = builder.features.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(EnumSet.copyOf(builder.features));
    }

    /**
     * Returns the shared empty upgrade snapshot.
     */
    public static UpgradeState empty() {
        return EMPTY;
    }

    /**
     * Returns a new mutable builder with no contributions.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Evaluates an attribute using this snapshot's modifiers.
     *
     * @param attribute   attribute to evaluate
     * @param defaultBase base value supplied by the storage implementation
     * @return the evaluated non-negative value
     */
    public double calculate(UpgradeAttribute attribute, double defaultBase) {
        return UpgradeModifier.calculate(getModifiers(attribute), defaultBase);
    }

    /**
     * Returns whether the specified feature is enabled.
     */
    public boolean hasFeature(StorageFeature feature) {
        return features.contains(Objects.requireNonNull(feature, "feature"));
    }

    /**
     * Returns the immutable modifiers for one attribute, in insertion order.
     */
    public List<UpgradeModifier> getModifiers(UpgradeAttribute attribute) {
        List<UpgradeModifier> values = modifiers.get(Objects.requireNonNull(attribute, "attribute"));
        return values == null ? Collections.emptyList() : values;
    }

    /**
     * Returns an immutable map whose values are immutable modifier lists.
     */
    public Map<UpgradeAttribute, List<UpgradeModifier>> getModifiers() {
        return modifiers;
    }

    /**
     * Returns the immutable set of enabled features.
     */
    public Set<StorageFeature> getFeatures() {
        return features;
    }

    /**
     * Mutable, reusable accumulator for creating immutable {@link UpgradeState} snapshots.
     */
    public static final class Builder {
        private final EnumMap<UpgradeAttribute, List<UpgradeModifier>> modifiers = new EnumMap<>(UpgradeAttribute.class);
        private final EnumSet<StorageFeature> features = EnumSet.noneOf(StorageFeature.class);

        /**
         * Adds one numeric contribution and returns this builder.
         */
        public Builder addModifier(UpgradeAttribute attribute, UpgradeModifier modifier) {
            modifiers.computeIfAbsent(Objects.requireNonNull(attribute, "attribute"), key -> new ArrayList<>()).add(Objects.requireNonNull(modifier, "modifier"));
            return this;
        }

        /**
         * Adds every contribution from a map and returns this builder.
         */
        public Builder addModifiers(Map<UpgradeAttribute, UpgradeModifier> values) {
            Objects.requireNonNull(values, "values");
            for (Map.Entry<UpgradeAttribute, UpgradeModifier> entry : values.entrySet()) {
                addModifier(entry.getKey(), entry.getValue());
            }
            return this;
        }

        /**
         * Enables a feature and returns this builder.
         */
        public Builder addFeature(StorageFeature feature) {
            features.add(Objects.requireNonNull(feature, "feature"));
            return this;
        }

        /**
         * Copies all contributions from an existing snapshot into this builder.
         */
        public Builder addAll(UpgradeState state) {
            Objects.requireNonNull(state, "state");
            for (Map.Entry<UpgradeAttribute, List<UpgradeModifier>> entry : state.modifiers.entrySet()) {
                for (UpgradeModifier modifier : entry.getValue()) {
                    addModifier(entry.getKey(), modifier);
                }
            }
            features.addAll(state.features);
            return this;
        }

        /**
         * Creates a detached immutable snapshot of the current contributions.
         */
        public UpgradeState build() {
            return new UpgradeState(this);
        }
    }
}
