package com.coplay.content.repository;

import com.coplay.content.domain.Board;
import com.coplay.content.domain.Comment;
import com.coplay.content.domain.Post;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for boards, posts and comments.
 * The production implementation would be a MyBatis repository.
 */
public interface ContentRepository {

    long nextBoardId();

    long nextPostId();

    long nextCommentId();

    Board saveBoard(Board board);

    Optional<Board> findBoardById(long boardId);

    List<Board> findAllBoards();

    Post savePost(Post post);

    Optional<Post> findPostById(long postId);

    List<Post> findPostsByBoard(long boardId);

    Comment saveComment(Comment comment);

    Optional<Comment> findCommentById(long commentId);

    List<Comment> findCommentsByPost(long postId);
}
