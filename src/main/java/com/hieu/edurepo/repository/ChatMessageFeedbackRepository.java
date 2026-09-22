package com.hieu.edurepo.repository;

import com.hieu.edurepo.entity.ChatMessageFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageFeedbackRepository extends JpaRepository<ChatMessageFeedback, Long> {

    Optional<ChatMessageFeedback> findByMessageIdAndUserId(String messageId, Long userId);

    Optional<ChatMessageFeedback> findByMessageIdAndClientIp(String messageId, String clientIp);

    List<ChatMessageFeedback> findByMessageId(String messageId);
}
