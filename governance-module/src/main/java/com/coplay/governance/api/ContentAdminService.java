package com.coplay.governance.api;

/**
 * Port to the content module: moderation actions on posts and comments.
 */
public interface ContentAdminService {

    void hidePost(long postId, String reason);

    void hideComment(long commentId, String reason);
}
