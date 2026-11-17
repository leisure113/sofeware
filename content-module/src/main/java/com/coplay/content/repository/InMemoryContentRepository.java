package com.coplay.content.repository;

import com.coplay.content.domain.Board;
import com.coplay.content.domain.Comment;
import com.coplay.content.domain.Post;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory repository used by the standalone demo and tests.
 * All mutating operations are synchronized and the maps are concurrent,
 * so the module is safe without a database.
 */
public final class InMemoryContentRepository implements ContentRepository {

    private final Map<Long, Board> boards = new ConcurrentHashMap<>();
    private final Map<Long, Post> posts = new ConcurrentHashMap<>();
    private final Map<Long, Comment> comments = new ConcurrentHashMap<>();
    private final AtomicLong boardIds = new AtomicLong(100);
    private final AtomicLong postIds = new AtomicLong(1000);
    private final AtomicLong commentIds = new AtomicLong(5000);

    @Override
    public long nextBoardId() {
        return boardIds.incrementAndGet();
    }

    @Override
    public long nextPostId() {
        return postIds.incrementAndGet();
    }

    @Override
    public long nextCommentId() {
        return commentIds.incrementAndGet();
    }

    @Override
    public synchronized Board saveBoard(Board board) {
        boards.put(board.getBoardId(), board);
        return board;
    }

    @Override
    public Optional<Board> findBoardById(long boardId) {
        return Optional.ofNullable(boards.get(boardId));
    }

    @Override
    public List<Board> findAllBoards() {
        return List.copyOf(boards.values());
    }

    @Override
    public synchronized Post savePost(Post post) {
        posts.put(post.getPostId(), post);
        return post;
    }

    @Override
    public Optional<Post> findPostById(long postId) {
        return Optional.ofNullable(posts.get(postId));
    }

    @Override
    public List<Post> findPostsByBoard(long boardId) {
        return posts.values().stream()
                .filter(post -> post.getBoardId() == boardId)
                .toList();
    }

    @Override
    public synchronized Comment saveComment(Comment comment) {
        comments.put(comment.getCommentId(), comment);
        return comment;
    }

    @Override
    public Optional<Comment> findCommentById(long commentId) {
        return Optional.ofNullable(comments.get(commentId));
    }

    @Override
    public List<Comment> findCommentsByPost(long postId) {
        return comments.values().stream()
                .filter(comment -> comment.getPostId() == postId)
                .toList();
    }
}
