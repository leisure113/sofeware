package com.coplay.content;

import com.coplay.content.domain.Board;
import com.coplay.content.domain.Comment;
import com.coplay.content.domain.Post;
import com.coplay.content.repository.InMemoryContentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("InMemoryContentRepository: ids, save and query behaviour")
class InMemoryContentRepositoryTest {

    private static final LocalDateTime CREATED_AT =
            ContentServiceTestSupport.NOW.minusDays(1);

    private InMemoryContentRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryContentRepository();
    }

    @Test
    @DisplayName("generates strictly increasing ids")
    void generatesIncreasingIds() {
        long firstBoardId = repository.nextBoardId();
        long secondBoardId = repository.nextBoardId();
        long firstPostId = repository.nextPostId();
        long secondPostId = repository.nextPostId();
        long firstCommentId = repository.nextCommentId();
        long secondCommentId = repository.nextCommentId();

        assertTrue(secondBoardId > firstBoardId);
        assertTrue(secondPostId > firstPostId);
        assertTrue(secondCommentId > firstCommentId);
    }

    @Test
    @DisplayName("saves and finds boards")
    void savesAndFindsBoards() {
        Board board = new Board(1L, 100L, "General discussion");

        assertSame(board, repository.saveBoard(board));
        assertSame(board, repository.findBoardById(1L).orElseThrow());
        List<Board> boards = repository.findAllBoards();
        assertEquals(1, boards.size());
        assertTrue(boards.contains(board));
        assertFalse(repository.findBoardById(999L).isPresent());
    }

    @Test
    @DisplayName("saves and finds posts, including per-board filtering")
    void savesAndFindsPosts() {
        Post first = new Post(101L, 1L, 2L, "First", "First body", CREATED_AT);
        Post second = new Post(102L, 2L, 2L, "Second", "Second body", CREATED_AT);

        assertSame(first, repository.savePost(first));
        repository.savePost(second);

        assertSame(first, repository.findPostById(101L).orElseThrow());
        assertEquals(List.of(first), repository.findPostsByBoard(1L));
        assertEquals(List.of(second), repository.findPostsByBoard(2L));
        assertTrue(repository.findPostsByBoard(999L).isEmpty());
        assertFalse(repository.findPostById(999L).isPresent());
    }

    @Test
    @DisplayName("saves and finds comments, including per-post filtering")
    void savesAndFindsComments() {
        Comment first = new Comment(501L, 101L, 3L, "First comment", CREATED_AT);
        Comment second = new Comment(502L, 102L, 3L, "Second comment", CREATED_AT);

        assertSame(first, repository.saveComment(first));
        repository.saveComment(second);

        assertSame(first, repository.findCommentById(501L).orElseThrow());
        assertEquals(List.of(first), repository.findCommentsByPost(101L));
        assertEquals(List.of(second), repository.findCommentsByPost(102L));
        assertTrue(repository.findCommentsByPost(999L).isEmpty());
        assertFalse(repository.findCommentById(999L).isPresent());
    }
}
