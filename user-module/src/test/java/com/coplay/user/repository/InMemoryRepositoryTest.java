package com.coplay.user.repository;

import com.coplay.user.domain.Role;
import com.coplay.user.domain.User;
import com.coplay.user.domain.UserStatus;
import com.coplay.user.domain.VerificationCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * In-memory adapters: id generation, lookups, latest-code semantics and deletes.
 */
class InMemoryRepositoryTest {

    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 11, 17, 10, 10);

    private final InMemoryUserRepository users = new InMemoryUserRepository();

    @Test
    @DisplayName("nextId is monotonic and skips ids that are already stored")
    void nextIdSkipsExistingIds() {
        assertEquals(1001L, users.nextId());
        users.save(user(1001L, "20231001", "u1001@coplay.edu"));
        users.save(user(1002L, "20231002", "u1002@coplay.edu"));

        assertEquals(1003L, users.nextId());
        assertEquals(1004L, users.nextId());
    }

    @Test
    @DisplayName("save stores a user that can be found by id")
    void saveAndFindById() {
        User alice = user(1001L, "20230001", "alice@coplay.edu");

        assertSame(alice, users.save(alice));
        assertSame(alice, users.findById(1001L).orElseThrow());
        assertTrue(users.findById(9999L).isEmpty());
    }

    @Test
    @DisplayName("findByStudentNo and findByEmail match exact stored values and return empty otherwise")
    void findByStudentNoAndEmail() {
        User alice = user(1001L, "20230001", "alice@coplay.edu");
        User bob = user(1002L, "20230002", "bob@coplay.edu");
        users.save(alice);
        users.save(bob);

        assertSame(alice, users.findByStudentNo("20230001").orElseThrow());
        assertSame(bob, users.findByEmail("bob@coplay.edu").orElseThrow());
        assertTrue(users.findByStudentNo("NOPE").isEmpty());
        assertTrue(users.findByEmail("nope@coplay.edu").isEmpty());
    }

    @Test
    @DisplayName("the code repository keeps only the latest code per email and deletes it")
    void verificationCodeRepositoryKeepsLatest() {
        InMemoryVerificationCodeRepository codes = new InMemoryVerificationCodeRepository();
        VerificationCode first = new VerificationCode(1001L, "alice@coplay.edu", "111111", EXPIRES_AT);
        VerificationCode second = new VerificationCode(1001L, "alice@coplay.edu", "222222", EXPIRES_AT.plusMinutes(5));

        assertSame(first, codes.save(first));
        assertSame(second, codes.save(second));

        assertSame(second, codes.findLatestByEmail("alice@coplay.edu").orElseThrow());
        assertEquals("222222", codes.findLatestByEmail("alice@coplay.edu").orElseThrow().getCode());

        codes.deleteByEmail("ghost@coplay.edu");
        assertTrue(codes.findLatestByEmail("alice@coplay.edu").isPresent());

        codes.deleteByEmail("alice@coplay.edu");
        assertTrue(codes.findLatestByEmail("alice@coplay.edu").isEmpty());
    }

    private static User user(long id, String studentNo, String email) {
        return new User(id, studentNo, email, "hash-" + id, "User " + id, UserStatus.ACTIVE, Role.USER);
    }
}
