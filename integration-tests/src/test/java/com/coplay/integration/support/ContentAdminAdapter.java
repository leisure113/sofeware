package com.coplay.integration.support;

import com.coplay.content.api.ContentService;

/**
 * Adapter from content module to governance moderation port.
 */
public final class ContentAdminAdapter implements com.coplay.governance.api.ContentAdminService {

    private final ContentService contentService;

    public ContentAdminAdapter(ContentService contentService) {
        this.contentService = contentService;
    }

    @Override
    public void hidePost(long postId, String reason) {
        contentService.hidePost(postId, reason);
    }

    @Override
    public void hideComment(long commentId, String reason) {
        contentService.hideComment(commentId, reason);
    }
}
