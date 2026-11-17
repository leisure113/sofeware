package com.coplay.content.api;

/**
 * Sensitive word port. The content module only knows this interface,
 * not the concrete dictionary implementation.
 */
public interface SensitiveWordFilter {

    boolean isBlocked(String text);
}
