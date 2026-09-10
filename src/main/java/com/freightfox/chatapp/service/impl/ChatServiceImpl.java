package com.freightfox.chatapp.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freightfox.chatapp.dto.response.ApiResponse;
import com.freightfox.chatapp.dto.response.ChatHistoryResponse;
import com.freightfox.chatapp.dto.response.ChatMessageDto;
import com.freightfox.chatapp.dto.response.ChatRoomMetaDto;
import com.freightfox.chatapp.exception.ChatRoomNotFoundException;
import com.freightfox.chatapp.exception.DuplicateChatRoomException;
import com.freightfox.chatapp.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // Redis Key Conventions
    public static final String KEY_ALL_ROOMS = "chatrooms:all";
    public static final String KEY_ROOM_META_PREFIX = "chatroom:%s:meta";
    public static final String KEY_ROOM_PARTICIPANTS_PREFIX = "chatroom:%s:participants";
    public static final String KEY_ROOM_MESSAGES_PREFIX = "chatroom:%s:messages";
    public static final String KEY_ROOM_CHANNEL_PREFIX = "chatroom:%s:channel";

    // Hash Field Constants
    public static final String FIELD_ROOM_ID = "roomId";
    public static final String FIELD_ROOM_NAME = "roomName";
    public static final String FIELD_CREATED_AT = "createdAt";

    private String getMetaKey(String roomId) {
        return String.format(KEY_ROOM_META_PREFIX, roomId);
    }

    private String getParticipantsKey(String roomId) {
        return String.format(KEY_ROOM_PARTICIPANTS_PREFIX, roomId);
    }

    private String getMessagesKey(String roomId) {
        return String.format(KEY_ROOM_MESSAGES_PREFIX, roomId);
    }

    private String getChannelName(String roomId) {
        return String.format(KEY_ROOM_CHANNEL_PREFIX, roomId);
    }

    @Override
    public ApiResponse createChatRoom(String roomName) {
        String sanitizedName = roomName.trim();
        String roomId = sanitizedName.toLowerCase();

        // Check if chat room already exists
        Boolean alreadyExists = redisTemplate.opsForSet().isMember(KEY_ALL_ROOMS, roomId);
        if (Boolean.TRUE.equals(alreadyExists) || Boolean.TRUE.equals(redisTemplate.hasKey(getMetaKey(roomId)))) {
            throw new DuplicateChatRoomException(String.format("Chat room '%s' already exists.", sanitizedName));
        }

        String nowIso = Instant.now().toString();

        // 1. Store chat room metadata in Redis Hash
        String metaKey = getMetaKey(roomId);
        Map<String, String> metaData = new HashMap<>();
        metaData.put(FIELD_ROOM_ID, roomId);
        metaData.put(FIELD_ROOM_NAME, sanitizedName);
        metaData.put(FIELD_CREATED_AT, nowIso);
        redisTemplate.opsForHash().putAll(metaKey, metaData);

        // 2. Add to active rooms set
        redisTemplate.opsForSet().add(KEY_ALL_ROOMS, roomId);

        log.info("Created chat room with name '{}' and roomId '{}'", sanitizedName, roomId);

        return ApiResponse.success(
                String.format("Chat room '%s' created successfully.", sanitizedName),
                roomId
        );
    }

    @Override
    public ApiResponse joinChatRoom(String roomId, String participant) {
        String normalizedRoomId = roomId.trim().toLowerCase();
        String sanitizedParticipant = participant.trim();

        // Validate chat room exists
        validateRoomExists(normalizedRoomId);

        // Store participant in Redis Set
        String participantsKey = getParticipantsKey(normalizedRoomId);
        redisTemplate.opsForSet().add(participantsKey, sanitizedParticipant);

        log.info("User '{}' joined chat room '{}'", sanitizedParticipant, normalizedRoomId);

        return ApiResponse.success(
                String.format("User '%s' joined chat room '%s'.", sanitizedParticipant, normalizedRoomId)
        );
    }

    @Override
    public ApiResponse sendMessage(String roomId, String participant, String message) {
        String normalizedRoomId = roomId.trim().toLowerCase();
        String sanitizedParticipant = participant.trim();

        // Validate chat room exists
        validateRoomExists(normalizedRoomId);

        // Automatically ensure participant is in the room's participant Set
        redisTemplate.opsForSet().add(getParticipantsKey(normalizedRoomId), sanitizedParticipant);

        // Construct ChatMessageDto with current UTC timestamp
        String timestamp = Instant.now().toString();
        ChatMessageDto chatMessage = ChatMessageDto.builder()
                .participant(sanitizedParticipant)
                .message(message)
                .timestamp(timestamp)
                .build();

        String messageJson;
        try {
            messageJson = objectMapper.writeValueAsString(chatMessage);
        } catch (JsonProcessingException e) {
            log.error("Error serializing chat message to JSON", e);
            throw new IllegalStateException("Failed to serialize chat message", e);
        }

        // 1. Store in Redis List in chronological order (RPUSH appends to tail)
        String messagesKey = getMessagesKey(normalizedRoomId);
        redisTemplate.opsForList().rightPush(messagesKey, messageJson);

        // 2. Broadcast message using Redis Pub/Sub for real-time subscribers
        String channelName = getChannelName(normalizedRoomId);
        redisTemplate.convertAndSend(channelName, messageJson);

        log.info("Message from '{}' sent and broadcasted in room '{}'", sanitizedParticipant, normalizedRoomId);

        return ApiResponse.success("Message sent successfully.");
    }

    @Override
    public ChatHistoryResponse getChatHistory(String roomId, int limit) {
        String normalizedRoomId = roomId.trim().toLowerCase();

        // Validate chat room exists
        validateRoomExists(normalizedRoomId);

        int effectiveLimit = (limit <= 0) ? 10 : Math.min(limit, 1000);
        String messagesKey = getMessagesKey(normalizedRoomId);

        // Redis LRANGE -limit -1 retrieves the last N elements in chronological order
        List<String> rawMessages = redisTemplate.opsForList().range(messagesKey, -effectiveLimit, -1);

        if (rawMessages == null || rawMessages.isEmpty()) {
            return ChatHistoryResponse.builder().messages(Collections.emptyList()).build();
        }

        List<ChatMessageDto> messages = new ArrayList<>();
        for (String rawJson : rawMessages) {
            try {
                ChatMessageDto dto = objectMapper.readValue(rawJson, ChatMessageDto.class);
                messages.add(dto);
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize message: {}", rawJson, e);
            }
        }

        return ChatHistoryResponse.builder()
                .messages(messages)
                .build();
    }

    @Override
    public ApiResponse deleteChatRoom(String roomId) {
        String normalizedRoomId = roomId.trim().toLowerCase();

        // Validate chat room exists
        validateRoomExists(normalizedRoomId);

        // Remove all Redis keys associated with this chat room
        String metaKey = getMetaKey(normalizedRoomId);
        String participantsKey = getParticipantsKey(normalizedRoomId);
        String messagesKey = getMessagesKey(normalizedRoomId);

        redisTemplate.delete(List.of(metaKey, participantsKey, messagesKey));
        redisTemplate.opsForSet().remove(KEY_ALL_ROOMS, normalizedRoomId);

        log.info("Deleted chat room '{}' and all associated data structures", normalizedRoomId);

        return ApiResponse.success(String.format("Chat room '%s' deleted successfully.", normalizedRoomId));
    }

    @Override
    public List<ChatRoomMetaDto> getAllChatRooms() {
        Set<String> roomIds = redisTemplate.opsForSet().members(KEY_ALL_ROOMS);
        if (roomIds == null || roomIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<ChatRoomMetaDto> roomList = new ArrayList<>();
        for (String id : roomIds) {
            String metaKey = getMetaKey(id);
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(metaKey);
            if (!entries.isEmpty()) {
                Long participantsCount = redisTemplate.opsForSet().size(getParticipantsKey(id));
                roomList.add(ChatRoomMetaDto.builder()
                        .roomId(id)
                        .roomName(String.valueOf(entries.getOrDefault(FIELD_ROOM_NAME, id)))
                        .createdAt(String.valueOf(entries.getOrDefault(FIELD_CREATED_AT, "")))
                        .participantCount(participantsCount != null ? participantsCount : 0L)
                        .build());
            }
        }
        return roomList;
    }

    @Override
    public Set<String> getParticipants(String roomId) {
        String normalizedRoomId = roomId.trim().toLowerCase();
        validateRoomExists(normalizedRoomId);

        Set<String> members = redisTemplate.opsForSet().members(getParticipantsKey(normalizedRoomId));
        return members != null ? members : Collections.emptySet();
    }

    private void validateRoomExists(String roomId) {
        Boolean isMember = redisTemplate.opsForSet().isMember(KEY_ALL_ROOMS, roomId);
        if (!Boolean.TRUE.equals(isMember)) {
            Boolean hasMeta = redisTemplate.hasKey(getMetaKey(roomId));
            if (!Boolean.TRUE.equals(hasMeta)) {
                throw new ChatRoomNotFoundException(String.format("Chat room '%s' does not exist.", roomId));
            }
        }
    }
}
