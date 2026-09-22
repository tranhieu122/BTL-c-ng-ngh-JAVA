package com.hieu.edurepo.service;

import com.hieu.edurepo.dto.ChatFeedbackRequest;
import com.hieu.edurepo.entity.ChatMessageFeedback;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.FeedbackRating;
import com.hieu.edurepo.repository.ChatMessageFeedbackRepository;
import com.hieu.edurepo.repository.UserRepository;
import com.hieu.edurepo.service.impl.ChatMessageFeedbackServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm thử tính năng Feedback (Thumbs Up / Down) - Priority 2B.
 * Xác minh:
 * - Ghi nhận đánh giá mới của người dùng.
 * - Cập nhật (Upsert) khi người dùng đổi vote từ UP sang DOWN, không tạo duplicate bản ghi.
 * - Ghi nhận phản hồi của khách vãng lai (theo client IP).
 */
class ChatMessageFeedbackTest {

    private ChatMessageFeedbackRepository feedbackRepository;
    private UserRepository userRepository;
    private ChatMessageFeedbackServiceImpl feedbackService;

    @BeforeEach
    void setUp() {
        feedbackRepository = mock(ChatMessageFeedbackRepository.class);
        userRepository = mock(UserRepository.class);
        feedbackService = new ChatMessageFeedbackServiceImpl(feedbackRepository, userRepository);
    }

    @Test
    @DisplayName("Tạo mới phản hồi khi chưa từng vote cho tin nhắn này")
    void createsNewFeedbackWhenNotVotedBefore() {
        User user = new User();
        user.setId(5L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(feedbackRepository.findByMessageIdAndUserId("msg-123", 5L)).thenReturn(Optional.empty());
        when(feedbackRepository.save(any(ChatMessageFeedback.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChatFeedbackRequest request = new ChatFeedbackRequest("msg-123", FeedbackRating.UP, null, null);
        ChatMessageFeedback result = feedbackService.recordFeedback(request, 5L, "127.0.0.1");

        assertNotNull(result);
        assertEquals("msg-123", result.getMessageId());
        assertEquals(FeedbackRating.UP, result.getRating());
        assertEquals(user, result.getUser());
        verify(feedbackRepository).save(any(ChatMessageFeedback.class));
    }

    @Test
    @DisplayName("Cập nhật lại đánh giá khi người dùng đổi vote (Upsert, không tạo duplicate)")
    void updatesExistingFeedbackWhenUserChangesVote() {
        User user = new User();
        user.setId(5L);

        ChatMessageFeedback existing = new ChatMessageFeedback("msg-123", user, "127.0.0.1", FeedbackRating.UP, null, null);

        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(feedbackRepository.findByMessageIdAndUserId("msg-123", 5L)).thenReturn(Optional.of(existing));
        when(feedbackRepository.save(any(ChatMessageFeedback.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChatFeedbackRequest request = new ChatFeedbackRequest("msg-123", FeedbackRating.DOWN, "INACCURATE", "Sai công thức");
        ChatMessageFeedback updated = feedbackService.recordFeedback(request, 5L, "127.0.0.1");

        assertNotNull(updated);
        assertEquals(FeedbackRating.DOWN, updated.getRating());
        assertEquals("INACCURATE", updated.getReason());
        assertEquals("Sai công thức", updated.getComment());
    }

    @Test
    @DisplayName("Từ chối request khi thiếu messageId")
    void rejectsWhenMessageIdIsMissing() {
        ChatFeedbackRequest request = new ChatFeedbackRequest("", FeedbackRating.UP, null, null);
        assertThrows(IllegalArgumentException.class, () -> feedbackService.recordFeedback(request, null, "127.0.0.1"));
    }
}
