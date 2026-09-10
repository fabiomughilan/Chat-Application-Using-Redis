package com.freightfox.chatapp.pubsub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.freightfox.chatapp.dto.response.ChatMessageDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageSubscriberTest {

    private ObjectMapper objectMapper;
    private ChatMessageSubscriber subscriber;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        subscriber = new ChatMessageSubscriber(objectMapper);
    }

    @Test
    @DisplayName("Test Case 3: Real-Time Messaging - Subscriber receives and parses broadcast message")
    void testReceivePubSubMessage() throws Exception {
        String channel = "chatroom:general:channel";
        ChatMessageDto originalMessage = new ChatMessageDto("guest_user", "Hello real-time!", "2024-01-01T10:00:00Z");
        String payload = objectMapper.writeValueAsString(originalMessage);

        DefaultMessage message = new DefaultMessage(
                channel.getBytes(StandardCharsets.UTF_8),
                payload.getBytes(StandardCharsets.UTF_8)
        );

        // Simulate Redis message listener container delivering the message
        subscriber.onMessage(message, null);

        List<ChatMessageDto> received = subscriber.getMessagesForChannel(channel);
        assertThat(received).hasSize(1);
        assertThat(received.get(0).getParticipant()).isEqualTo("guest_user");
        assertThat(received.get(0).getMessage()).isEqualTo("Hello real-time!");
        assertThat(received.get(0).getTimestamp()).isEqualTo("2024-01-01T10:00:00Z");
    }
}
