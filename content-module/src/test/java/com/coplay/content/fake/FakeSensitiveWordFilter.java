package com.coplay.content.fake;

import com.coplay.content.api.SensitiveWordFilter;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class FakeSensitiveWordFilter implements SensitiveWordFilter {

    private final Set<String> blockedWords = new HashSet<>(Set.of("forbidden"));

    public FakeSensitiveWordFilter block(String word) {
        blockedWords.add(word.toLowerCase(Locale.ROOT));
        return this;
    }

    @Override
    public boolean isBlocked(String text) {
        if (text == null) {
            return false;
        }
        String lowerCase = text.toLowerCase(Locale.ROOT);
        return blockedWords.stream().anyMatch(lowerCase::contains);
    }
}
