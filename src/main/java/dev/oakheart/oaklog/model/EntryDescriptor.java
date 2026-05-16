package dev.oakheart.oaklog.model;

public record EntryDescriptor(
        String namespace,
        String key,
        Category category,
        String displayName,
        String iconMaterial,
        Integer iconModelData,
        String detectionMeta
) {
    public String namespacedKey() {
        return namespace + ":" + key;
    }
}
