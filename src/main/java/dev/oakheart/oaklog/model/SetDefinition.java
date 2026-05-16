package dev.oakheart.oaklog.model;

import java.util.List;

public record SetDefinition(
        String id,
        String displayName,
        Category category,
        boolean tab,
        List<SetMatcher> matchers
) {}
