package com.coplay.integration.support;

import java.util.Set;

/**
 * Deterministic sensitive-word filter adapter for integration tests.
 */
public final class SensitiveWordAdapter implements com.coplay.content.api.SensitiveWordFilter {

    private final Set<String> blockedWords;

    public SensitiveWordAdapter(Set<String> blockedWords) {
        this.blockedWords = Set.copyOf(blockedWords);
    }

    @Override
    public boolean isBlocked(String text) {
        if (text == null) {
            return false;
        }
        return blockedWords.stream().anyMatch(text::contains);
    }
}
