package com.coplay.content;

import com.coplay.content.domain.Post;
import com.coplay.content.domain.PostStatus;
import com.coplay.content.dto.PostBrief;
import com.coplay.content.dto.PostDetail;
import com.coplay.content.support.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("listPosts: board filtering, visibility and ordering")
class ListPostsTest extends ContentServiceTestSupport {

    @Test
    @DisplayName("rejects an unknown board")
    void rejectsUnknownBoard() {
        assertBizError(ErrorCode.BOARD_NOT_FOUND, () -> service.listPosts(777L));
    }

    @Test
    @DisplayName("returns an empty list for a board without posts")
    void returnsEmptyListForBoardWithoutPosts() {
        assertTrue(service.listPosts(SECOND_BOARD_ID).isEmpty());
    }

    @Test
    @DisplayName("returns only PUBLISHED posts and skips hidden and deleted ones")
    void returnsOnlyPublishedPosts() {
        Post published = seedPost(101L, BOARD_ID, AUTHOR_ID, "Published topic",
                "Body of the published topic.", NOW.minusHours(3));
        Post hidden = seedPost(102L, BOARD_ID, AUTHOR_ID, "Hidden topic",
                "Body of the hidden topic.", NOW.minusHours(2));
        Post deleted = seedPost(103L, BOARD_ID, AUTHOR_ID, "Deleted topic",
                "Body of the deleted topic.", NOW.minusHours(1));
        service.hidePost(hidden.getPostId(), "spam");
        forcePostStatus(deleted, PostStatus.DELETED);

        List<PostBrief> posts = service.listPosts(BOARD_ID);

        assertEquals(List.of(published.getPostId()), ids(posts));
        assertEquals(PostStatus.PUBLISHED, posts.get(0).status());
    }

    @Test
    @DisplayName("orders posts by createdAt descending")
    void ordersByCreatedAtDescending() {
        seedPost(101L, BOARD_ID, AUTHOR_ID, "Oldest topic", "Oldest body.", NOW.minusMinutes(30));
        seedPost(102L, BOARD_ID, AUTHOR_ID, "Newest topic", "Newest body.", NOW.minusMinutes(10));
        seedPost(103L, BOARD_ID, AUTHOR_ID, "Middle topic", "Middle body.", NOW.minusMinutes(20));

        List<PostBrief> posts = service.listPosts(BOARD_ID);

        assertEquals(List.of(102L, 103L, 101L), ids(posts));
    }

    @Test
    @DisplayName("orders posts with the same createdAt by post id descending")
    void ordersByIdDescendingWhenCreatedAtTies() {
        LocalDateTime sharedTime = NOW.minusMinutes(5);
        seedPost(201L, BOARD_ID, AUTHOR_ID, "First tied topic", "First tied body.", sharedTime);
        seedPost(203L, BOARD_ID, AUTHOR_ID, "Third tied topic", "Third tied body.", sharedTime);
        seedPost(202L, BOARD_ID, AUTHOR_ID, "Second tied topic", "Second tied body.", sharedTime);

        List<PostBrief> posts = service.listPosts(BOARD_ID);

        assertEquals(List.of(203L, 202L, 201L), ids(posts));
    }

    @Test
    @DisplayName("filters posts by board even when another board has newer posts")
    void filtersByBoard() {
        PostDetail boardOnePost = createPost("Board one topic", "Body on board one.");
        seedPost(301L, SECOND_BOARD_ID, AUTHOR_ID, "Board two topic",
                "Body on board two.", NOW.plusMinutes(1));

        assertEquals(List.of(boardOnePost.postId()), ids(service.listPosts(BOARD_ID)));
        assertEquals(List.of(301L), ids(service.listPosts(SECOND_BOARD_ID)));
    }

    @Test
    @DisplayName("hiding the only post of a board empties its list")
    void hidingTheOnlyPostEmptiesTheList() {
        PostDetail post = createPost("Lonely topic", "The only post on this board.");
        service.hidePost(post.postId(), "off-topic");

        assertTrue(service.listPosts(BOARD_ID).isEmpty());
    }

    @Test
    @DisplayName("returns brief views with id, board, author, title, status and createdAt")
    void returnsBriefViews() {
        PostDetail created = createPost("Brief topic", "A body that is not part of the brief view.");

        PostBrief post = service.listPosts(BOARD_ID).get(0);

        assertEquals(created.postId(), post.postId());
        assertEquals(BOARD_ID, post.boardId());
        assertEquals(AUTHOR_ID, post.authorId());
        assertEquals("Brief topic", post.title());
        assertEquals(PostStatus.PUBLISHED, post.status());
        assertEquals(NOW, post.createdAt());
    }

    private static List<Long> ids(List<PostBrief> posts) {
        return posts.stream().map(PostBrief::postId).toList();
    }
}
