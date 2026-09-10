package com.freightfox.chatapp.pubsub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.freightfox.chatapp.dto.response.ChatMessageDto;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

/**
 * Real-time Redis Pub/Sub message subscriber.
 * Listens for broadcasts on chat room channels and delivers them to local subscribers/logs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;

    // In-memory buffer of recent broadcast messages by channel for inspection/real-time tracking
    @Getter
    private final Map<String, List<ChatMessageDto>> receivedMessages = new ConcurrentHashMap<>();

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String body = new String(message.getBody(), StandardCharsets.UTF_8);

        log.info("Received real-time Pub/Sub broadcast on channel [{}]: {}", channel, body);

        try {
            ChatMessageDto chatMessage = objectMapper.readValue(body, ChatMessageDto.class);
            receivedMessages.computeIfAbsent(channel, k -> new CopyOnWriteArrayList<>()).add(chatMessage);
        } catch (Exception e) {
            log.warn("Failed to parse Pub/Sub message payload: {}", body, e);
        }
    }

    public List<ChatMessageDto> getMessagesForChannel(String channel) {
        return receivedMessages.getOrDefault(channel, List.of());
    }

    public void clear() {
        receivedMessages.clear();
    }
}
