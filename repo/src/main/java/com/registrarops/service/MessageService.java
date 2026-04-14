package com.registrarops.service;

import com.registrarops.entity.Message;
import com.registrarops.entity.MessagePreference;
import com.registrarops.repository.MessagePreferenceRepository;
import com.registrarops.repository.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * In-app messaging.
 *
 * Phase 4 introduces the basic insert path so order state transitions can
 * notify the student. Phase 6 extends this same class with:
 *   - quiet-hours deferral (10 PM – 7 AM)
 *   - muted-category skip
 *   - duplicate-message threading (same category+relatedId within 1 hour)
 *   - unread badge query
 *
 * Offline-only: there is intentionally no integration with WeChat, email or
 * any external channel — every notification is in-app per the prompt.
 */
@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);
    private static final long DEDUP_WINDOW_HOURS = 1;

    private final MessageRepository messageRepository;
    private final MessagePreferenceRepository preferenceRepository;
    private final PolicySettingService policySettingService;

    @org.springframework.beans.factory.annotation.Autowired
    public MessageService(MessageRepository messageRepository,
                          MessagePreferenceRepository preferenceRepository,
                          PolicySettingService policySettingService) {
        this.messageRepository = messageRepository;
        this.preferenceRepository = preferenceRepository;
        this.policySettingService = policySettingService;
    }

    /** Test ctor without policy service. */
    public MessageService(MessageRepository messageRepository,
                          MessagePreferenceRepository preferenceRepository) {
        this(messageRepository, preferenceRepository, null);
    }

    @Transactional
    public Optional<Message> send(Long recipientId,
                                  String category,
                                  String subject,
                                  String body,
                                  Long relatedId,
                                  String relatedType) {
        MessagePreference pref = preferenceRepository.findByUserId(recipientId).orElse(null);

        // 1. muted category → drop
        if (pref != null && pref.getMutedCategories() != null
                && !pref.getMutedCategories().isBlank()) {
            for (String muted : pref.getMutedCategories().split(",")) {
                if (muted.trim().equalsIgnoreCase(category)) {
                    log.debug("muted category {} for user {} — message dropped", category, recipientId);
                    return Optional.empty();
                }
            }
        }

        // 2. quiet-hours → defer to next quiet-end hour. Per-user preference wins;
        // otherwise the system-wide PolicySettingService values apply, which an
        // admin can change at runtime via the policy admin surface.
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deliverAt = null;
        int quietStart;
        int quietEnd;
        if (pref != null) {
            quietStart = pref.getQuietStartHour();
            quietEnd   = pref.getQuietEndHour();
        } else if (policySettingService != null) {
            quietStart = policySettingService.getInt("notifications.quiet_start_hour", 22);
            quietEnd   = policySettingService.getInt("notifications.quiet_end_hour", 7);
        } else {
            quietStart = 22; quietEnd = 7;
        }
        if (isInQuietHours(now.getHour(), quietStart, quietEnd)) {
            deliverAt = now.withHour(quietEnd).withMinute(0).withSecond(0).withNano(0);
            if (!deliverAt.isAfter(now)) deliverAt = deliverAt.plusDays(1);
        }

        // 3. duplicate dedup: same (recipient, category, relatedType, relatedId) within last hour
        if (relatedId != null && relatedType != null) {
            LocalDateTime since = now.minusHours(DEDUP_WINDOW_HOURS);
            Optional<Message> existing = messageRepository.findRecentDuplicate(
                    recipientId, category, relatedType, relatedId, since);
            if (existing.isPresent()) {
                Message thread = existing.get();
                thread.setThreadCount(thread.getThreadCount() + 1);
                if (thread.getThreadKey() == null) {
                    thread.setThreadKey(category + ":" + relatedType + ":" + relatedId);
                }
                thread.setIsRead(false);
                messageRepository.save(thread);
                return Optional.of(thread);
            }
        }

        Message msg = new Message();
        msg.setRecipientId(recipientId);
        msg.setSenderType("SYSTEM");
        msg.setCategory(category);
        msg.setSubject(subject);
        msg.setBody(body);
        msg.setRelatedId(relatedId);
        msg.setRelatedType(relatedType);
        msg.setIsRead(false);
        msg.setCreatedAt(now);
        msg.setDeliverAt(deliverAt);
        return Optional.of(messageRepository.save(msg));
    }

    public long getUnreadCount(Long userId) {
        return messageRepository.countUnreadDelivered(userId, LocalDateTime.now());
    }

    public java.util.List<Message> listForUser(Long userId) {
        return messageRepository.findDeliveredForUser(userId, LocalDateTime.now());
    }

    @Transactional
    public void markAllRead(Long userId) {
        for (Message m : listForUser(userId)) {
            if (!Boolean.TRUE.equals(m.getIsRead())) {
                m.setIsRead(true);
                messageRepository.save(m);
            }
        }
    }

    @Transactional
    public void markRead(Long messageId, Long userId) {
        messageRepository.findById(messageId).ifPresent(m -> {
            if (m.getRecipientId().equals(userId)) {
                m.setIsRead(true);
                messageRepository.save(m);
            }
        });
    }

    @Transactional
    public MessagePreference muteCategory(Long userId, String category) {
        MessagePreference p = preferenceRepository.findByUserId(userId)
                .orElseGet(() -> {
                    MessagePreference fresh = new MessagePreference();
                    fresh.setUserId(userId);
                    fresh.setMutedCategories("");
                    fresh.setQuietStartHour(22);
                    fresh.setQuietEndHour(7);
                    return fresh;
                });
        String existing = p.getMutedCategories() == null ? "" : p.getMutedCategories();
        boolean already = false;
        for (String s : existing.split(",")) {
            if (s.trim().equalsIgnoreCase(category)) { already = true; break; }
        }
        if (!already) {
            p.setMutedCategories(existing.isBlank() ? category : existing + "," + category);
        }
        return preferenceRepository.save(p);
    }

    @Transactional
    public MessagePreference updateQuietHours(Long userId, int startHour, int endHour) {
        if (startHour < 0 || startHour > 23 || endHour < 0 || endHour > 23) {
            throw new IllegalArgumentException(
                    "quiet hours must be in [0,23]; got start=" + startHour + " end=" + endHour);
        }
        MessagePreference p = preferenceRepository.findByUserId(userId)
                .orElseGet(() -> {
                    MessagePreference fresh = new MessagePreference();
                    fresh.setUserId(userId);
                    fresh.setMutedCategories("");
                    return fresh;
                });
        p.setQuietStartHour(startHour);
        p.setQuietEndHour(endHour);
        return preferenceRepository.save(p);
    }

    public MessagePreference getPreferences(Long userId) {
        return preferenceRepository.findByUserId(userId).orElse(null);
    }

    /** Visible for unit tests: true if {@code hour} falls inside [start, end) wrapping at midnight. */
    public static boolean isInQuietHours(int hour, int start, int end) {
        if (start == end) return false;
        if (start < end) return hour >= start && hour < end;
        // wraps midnight: e.g. start=22, end=7 → quiet if hour>=22 OR hour<7
        return hour >= start || hour < end;
    }
}
