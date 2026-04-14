package com.registrarops.unit;

import com.registrarops.entity.Message;
import com.registrarops.entity.MessagePreference;
import com.registrarops.repository.MessagePreferenceRepository;
import com.registrarops.repository.MessageRepository;
import com.registrarops.service.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MessageServiceTest {

    private MessageRepository messageRepository;
    private MessagePreferenceRepository preferenceRepository;
    private MessageService messageService;

    @BeforeEach
    void setUp() {
        messageRepository = mock(MessageRepository.class);
        preferenceRepository = mock(MessagePreferenceRepository.class);
        messageService = new MessageService(messageRepository, preferenceRepository);
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static MessagePreference pref(int start, int end, String muted) {
        MessagePreference p = new MessagePreference();
        p.setUserId(1L);
        p.setQuietStartHour(start);
        p.setQuietEndHour(end);
        p.setMutedCategories(muted);
        return p;
    }

    @Test
    void testQuietHoursWrapAroundMidnight() {
        // 22:00–07:00 should mark every hour 22, 23, 0, 1, ..., 6 as quiet.
        for (int h : new int[]{22, 23, 0, 6}) {
            assertTrue(MessageService.isInQuietHours(h, 22, 7), "expected quiet at " + h);
        }
        for (int h : new int[]{7, 12, 21}) {
            assertFalse(MessageService.isInQuietHours(h, 22, 7), "expected NOT quiet at " + h);
        }
    }

    @Test
    void testMutedCategorySkips() {
        when(preferenceRepository.findByUserId(1L)).thenReturn(Optional.of(pref(0, 0, "ORDER")));
        Optional<Message> sent = messageService.send(1L, "ORDER", "Hi", "body", 99L, "Order");
        assertTrue(sent.isEmpty());
        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    void testNonMutedCategoryDelivered() {
        when(preferenceRepository.findByUserId(1L)).thenReturn(Optional.of(pref(0, 0, "ORDER")));
        Optional<Message> sent = messageService.send(1L, "GRADE", "Hi", "body", 99L, "Grade");
        assertTrue(sent.isPresent());
    }

    @Test
    void testDuplicateMessageThreads() {
        Message existing = new Message();
        existing.setId(7L);
        existing.setRecipientId(1L);
        existing.setCategory("ORDER");
        existing.setRelatedId(99L);
        existing.setRelatedType("Order");
        existing.setThreadCount(1);
        existing.setCreatedAt(LocalDateTime.now());

        when(preferenceRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(messageRepository.findRecentDuplicate(any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(existing));

        Optional<Message> sent = messageService.send(1L, "ORDER", "Subject", "body", 99L, "Order");

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(captor.capture());
        Message saved = captor.getValue();
        assertEquals(2, saved.getThreadCount());
        assertNotNull(saved.getThreadKey());
        assertSame(existing, sent.get());
    }

    @Test
    void testFreshMessageGetsThreadCountOne() {
        when(preferenceRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(messageRepository.findRecentDuplicate(any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        Optional<Message> sent = messageService.send(1L, "ORDER", "Subject", "body", 99L, "Order");
        assertTrue(sent.isPresent());
        assertEquals(1, sent.get().getThreadCount());
    }

    @Test
    void testMuteCategoryHelper() {
        when(preferenceRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(preferenceRepository.save(any(MessagePreference.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        MessagePreference p = messageService.muteCategory(1L, "ORDER");
        assertEquals("ORDER", p.getMutedCategories());

        // Mute again should not duplicate.
        when(preferenceRepository.findByUserId(1L)).thenReturn(Optional.of(p));
        MessagePreference p2 = messageService.muteCategory(1L, "ORDER");
        assertEquals("ORDER", p2.getMutedCategories());
    }
}
