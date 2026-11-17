package com.coplay.content;

import com.coplay.content.domain.CommentStatus;
import com.coplay.content.domain.PostStatus;
import com.coplay.content.dto.CommentBrief;
import com.coplay.content.dto.CreateCommentCommand;
import com.coplay.content.dto.CreatePostCommand;
import com.coplay.content.dto.PostBrief;
import com.coplay.content.dto.PostDetail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DTO records: generated equals/hashCode/toString")
class DtoContractTest {

    private static final LocalDateTime CREATED_AT = ContentServiceTestSupport.NOW.minusHours(1);

    @Test
    @DisplayName("PostDetail is a value object")
    void postDetailIsAValueObject() {
        assertRecordContract(new PostDetail(1L, 2L, 3L, "Title", "Content",
                PostStatus.PUBLISHED, CREATED_AT));
    }

    @Test
    @DisplayName("PostBrief is a value object")
    void postBriefIsAValueObject() {
        assertRecordContract(new PostBrief(1L, 2L, 3L, "Title", PostStatus.PUBLISHED, CREATED_AT));
    }

    @Test
    @DisplayName("CommentBrief is a value object")
    void commentBriefIsAValueObject() {
        assertRecordContract(new CommentBrief(1L, 2L, 3L, "Content",
                CommentStatus.PUBLISHED, CREATED_AT));
    }

    @Test
    @DisplayName("CreatePostCommand is a value object")
    void createPostCommandIsAValueObject() {
        assertRecordContract(new CreatePostCommand(1L, 2L, "Title", "Content"));
    }

    @Test
    @DisplayName("CreateCommentCommand is a value object")
    void createCommentCommandIsAValueObject() {
        assertRecordContract(new CreateCommentCommand(1L, 2L, "Content"));
    }

    /**
     * Checks reflexive/symmetric equality, hashCode and toString, then changes every
     * component in turn to prove that each component participates in equals.
     */
    private static <T extends Record> void assertRecordContract(T record) {
        @SuppressWarnings("unchecked")
        Class<T> type = (Class<T>) record.getClass();
        RecordComponent[] components = type.getRecordComponents();
        Object[] values = readComponents(record, components);

        T equalCopy = newRecord(type, components, values);
        assertTrue(record.equals(record));
        assertEquals(record, equalCopy);
        assertEquals(equalCopy, record);
        assertEquals(record.hashCode(), equalCopy.hashCode());
        assertEquals(record.toString(), equalCopy.toString());
        assertFalse(record.equals(null));
        assertFalse(record.equals(new Object()));

        for (int i = 0; i < components.length; i++) {
            Object[] variantValues = values.clone();
            variantValues[i] = variantValue(values[i]);
            T variant = newRecord(type, components, variantValues);
            assertNotEquals(record, variant, "component " + components[i].getName());
            assertNotEquals(record.toString(), variant.toString(),
                    "toString for component " + components[i].getName());
        }
    }

    private static Object[] readComponents(Record record, RecordComponent[] components) {
        Object[] values = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            try {
                values[i] = components[i].getAccessor().invoke(record);
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError("Unable to read " + components[i].getName(), exception);
            }
        }
        return values;
    }

    private static <T extends Record> T newRecord(Class<T> type,
                                                  RecordComponent[] components,
                                                  Object[] values) {
        Class<?>[] parameterTypes = Arrays.stream(components)
                .map(RecordComponent::getType)
                .toArray(Class<?>[]::new);
        try {
            Constructor<T> constructor = type.getDeclaredConstructor(parameterTypes);
            return constructor.newInstance(values);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to create " + type.getSimpleName(), exception);
        }
    }

    private static Object variantValue(Object value) {
        if (value instanceof Long longValue) {
            return longValue + 1;
        }
        if (value instanceof String stringValue) {
            return stringValue + " (variant)";
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.plusSeconds(1);
        }
        if (value instanceof PostStatus postStatus) {
            return postStatus == PostStatus.PUBLISHED ? PostStatus.HIDDEN : PostStatus.PUBLISHED;
        }
        if (value instanceof CommentStatus commentStatus) {
            return commentStatus == CommentStatus.PUBLISHED
                    ? CommentStatus.HIDDEN : CommentStatus.PUBLISHED;
        }
        throw new IllegalArgumentException("Unsupported component value: " + value);
    }
}
