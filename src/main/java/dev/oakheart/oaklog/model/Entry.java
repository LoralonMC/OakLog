package dev.oakheart.oaklog.model;

public record Entry(
        int id,
        String namespace,
        String key,
        Category category,
        String displayName,
        String iconMaterial,
        Integer iconModelData,
        String detectionMeta,
        Long retiredAt,
        long firstSeenAt
) {
    public boolean isRetired() {
        return retiredAt != null;
    }

    public String namespacedKey() {
        return namespace + ":" + key;
    }
}
