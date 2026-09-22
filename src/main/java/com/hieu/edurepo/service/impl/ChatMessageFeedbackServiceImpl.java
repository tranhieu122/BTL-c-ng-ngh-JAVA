package com.hieu.edurepo.service.impl;

import com.hieu.edurepo.dto.ChatFeedbackRequest;
import com.hieu.edurepo.entity.ChatMessageFeedback;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.repository.ChatMessageFeedbackRepository;
import com.hieu.edurepo.repository.UserRepository;
import com.hieu.edurepo.service.ChatMessageFeedbackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class ChatMessageFeedbackServiceImpl implements ChatMessageFeedbackService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatMessageFeedbackServiceImpl.class);

    private final ChatMessageFeedbackRepository feedbackRepository;
    private final UserRepository userRepository;

    public ChatMessageFeedbackServiceImpl(ChatMessageFeedbackRepository feedbackRepository,
                                         UserRepository userRepository) {
        this.feedbackRepository = feedbackRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public ChatMessageFeedback recordFeedback(ChatFeedbackRequest request, Long userId, String clientIp) {
        if (request == null || request.messageId() == null || request.messageId().isBlank()) {
            throw new IllegalArgumentException("Mã tin nhắn không được để trống.");
        }

        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId).orElse(null);
        }

        Optional<ChatMessageFeedback> existingOpt;
        if (user != null) {
            existingOpt = feedbackRepository.findByMessageIdAndUserId(request.messageId(), user.getId());
        } else {
            existingOpt = feedbackRepository.findByMessageIdAndClientIp(request.messageId(), clientIp);
        }

        ChatMessageFeedback feedback;
        if (existingOpt.isPresent()) {
            feedback = existingOpt.get();
            feedback.setRating(request.rating());
            feedback.setReason(request.reason());
            feedback.setComment(request.comment());
            LOGGER.info("Cập nhật phản hồi đánh giá cho tin nhắn {}: {} (User: {}, IP: {})",
                    request.messageId(), request.rating(), userId, clientIp);
        } else {
            feedback = new ChatMessageFeedback(
                    request.messageId(),
                    user,
                    clientIp,
                    request.rating(),
                    request.reason(),
                    request.comment()
            );
            LOGGER.info("Tạo mới phản hồi đánh giá cho tin nhắn {}: {} (User: {}, IP: {})",
                    request.messageId(), request.rating(), userId, clientIp);
        }

        return feedbackRepository.save(feedback);
    }
}
