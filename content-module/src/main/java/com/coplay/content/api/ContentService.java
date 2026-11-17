package com.coplay.content.api;

import com.coplay.content.dto.CommentBrief;
import com.coplay.content.dto.CreateCommentCommand;
import com.coplay.content.dto.CreatePostCommand;
import com.coplay.content.dto.PostBrief;
import com.coplay.content.dto.PostDetail;

import java.util.List;

/**
 * Public use cases of the content module.
 */
public interface ContentService {

    PostDetail createPost(CreatePostCommand command);

    PostDetail getPost(long postId);

    PostDetail editPost(long postId, long authorId, String title, String content);

    List<PostBrief> listPosts(long boardId);

    CommentBrief addComment(CreateCommentCommand command);

    List<CommentBrief> listComments(long postId);

    void hidePost(long postId, String reason);

    void hideComment(long commentId, String reason);
}
