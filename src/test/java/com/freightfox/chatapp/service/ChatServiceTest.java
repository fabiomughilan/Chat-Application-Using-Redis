package com.freightfox.chatapp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.freightfox.chatapp.dto.response.ApiResponse;
import com.freightfox.chatapp.dto.response.ChatHistoryResponse;
import com.freightfox.chatapp.dto.response.ChatMessageDto;
import com.freightfox.chatapp.dto.response.ChatRoomMetaDto;
import com.freightfox.chatapp.exception.ChatRoomNotFoundException;
import com.freightfox.chatapp.exception.DuplicateChatRoomException;
import com.freightfox.chatapp.service.impl.ChatServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private ListOperations<String, String> listOperations;

    @Captor
    private ArgumentCaptor<String> messageCaptor;

    private ObjectMapper objectMapper;
    private ChatServiceImpl chatService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        lenient().when(redisTemplate.opsForList()).thenReturn(listOperations);

        chatService = new ChatServiceImpl(redisTemplate, objectMapper);
    }

    // ==========================================
    // Test Case 1: Create and Join Chat Room
    // ==========================================

    @Test
    @DisplayName("Test Case 1: Create chat room successfully and store in Redis Hash and Set")
    void testCreateChatRoom_Success() {
        when(setOperations.isMember(ChatServiceImpl.KEY_ALL_ROOMS, "general")).thenReturn(false);
        when(redisTemplate.hasKey("chatroom:general:meta")).thenReturn(false);

        ApiResponse response = chatService.createChatRoom("general");

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getRoomId()).isEqualTo("general");
        assertThat(response.getMessage()).contains("created successfully");

        // Verify Hash metadata is stored
        verify(hashOperations).putAll(eq("chatroom:general:meta"), anyMap());
        // Verify roomId is added to active rooms Set
        verify(setOperations).add(ChatServiceImpl.KEY_ALL_ROOMS, "general");
    }

    @Test
    @DisplayName("Test Case 1: Join chat room successfully and store in Redis Set")
    void testJoinChatRoom_Success() {
        when(setOperations.isMember(ChatServiceImpl.KEY_ALL_ROOMS, "general")).thenReturn(true);

        ApiResponse response = chatService.joinChatRoom("general", "guest_user");

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getMessage()).isEqualTo("User 'guest_user' joined chat room 'general'.");

        // Verify participant added to Set
        verify(setOperations).add("chatroom:general:participants", "guest_user");
    }

    // ==========================================
    // Test Case 2: Send and Retrieve Messages
    // ==========================================

    @Test
    @DisplayName("Test Case 2: Send message stores in List and broadcasts via Pub/Sub")
    void testSendMessage_Success() {
        when(setOperations.isMember(ChatServiceImpl.KEY_ALL_ROOMS, "general")).thenReturn(true);

        ApiResponse response = chatService.sendMessage("general", "guest_user", "Hello, everyone!");

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getMessage()).isEqualTo("Message sent successfully.");

        // Verify message pushed to Redis List (RPUSH)
        verify(listOperations).rightPush(eq("chatroom:general:messages"), messageCaptor.capture());
        String storedJson = messageCaptor.getValue();
        assertThat(storedJson).contains("guest_user", "Hello, everyone!");

        // Verify message broadcasted via Pub/Sub
        verify(redisTemplate).convertAndSend(eq("chatroom:general:channel"), eq(storedJson));
    }

    @Test
    @DisplayName("Test Case 2: Retrieve chat history in correct chronological order")
    void testGetChatHistory_Success() throws Exception {
        when(setOperations.isMember(ChatServiceImpl.KEY_ALL_ROOMS, "general")).thenReturn(true);

        ChatMessageDto msg1 = new ChatMessageDto("guest_user", "Hello, everyone!", "2024-01-01T10:00:00Z");
        ChatMessageDto msg2 = new ChatMessageDto("another_user", "Hi, guest_user!", "2024-01-01T10:01:00Z");

        String json1 = objectMapper.writeValueAsString(msg1);
        String json2 = objectMapper.writeValueAsString(msg2);

        when(listOperations.range("chatroom:general:messages", -10, -1)).thenReturn(List.of(json1, json2));

        ChatHistoryResponse response = chatService.getChatHistory("general", 10);

        assertThat(response.getMessages()).hasSize(2);
        assertThat(response.getMessages().get(0).getParticipant()).isEqualTo("guest_user");
        assertThat(response.getMessages().get(0).getMessage()).isEqualTo("Hello, everyone!");
        assertThat(response.getMessages().get(1).getParticipant()).isEqualTo("another_user");
        assertThat(response.getMessages().get(1).getMessage()).isEqualTo("Hi, guest_user!");
    }

    // ==========================================
    // Test Case 4: Error Handling
    // ==========================================

    @Test
    @DisplayName("Test Case 4: Attempt to create duplicate chat room throws DuplicateChatRoomException")
    void testCreateChatRoom_Duplicate() {
        when(setOperations.isMember(ChatServiceImpl.KEY_ALL_ROOMS, "general")).thenReturn(true);

        assertThatThrownBy(() -> chatService.createChatRoom("general"))
                .isInstanceOf(DuplicateChatRoomException.class)
                .hasMessage("Chat room 'general' already exists.");

        verify(hashOperations, never()).putAll(anyString(), anyMap());
    }

    @Test
    @DisplayName("Test Case 4: Attempt to send message to non-existent chat room throws ChatRoomNotFoundException")
    void testSendMessage_NonExistentRoom() {
        when(setOperations.isMember(ChatServiceImpl.KEY_ALL_ROOMS, "random_room")).thenReturn(false);
        when(redisTemplate.hasKey("chatroom:random_room:meta")).thenReturn(false);

        assertThatThrownBy(() -> chatService.sendMessage("random_room", "guest_user", "Hello"))
                .isInstanceOf(ChatRoomNotFoundException.class)
                .hasMessage("Chat room 'random_room' does not exist.");

        verify(listOperations, never()).rightPush(anyString(), anyString());
        verify(redisTemplate, never()).convertAndSend(anyString(), any());
    }

    @Test
    @DisplayName("Test Case 4: Attempt to join non-existent chat room throws ChatRoomNotFoundException")
    void testJoinChatRoom_NonExistentRoom() {
        when(setOperations.isMember(ChatServiceImpl.KEY_ALL_ROOMS, "non_existent")).thenReturn(false);
        when(redisTemplate.hasKey("chatroom:non_existent:meta")).thenReturn(false);

        assertThatThrownBy(() -> chatService.joinChatRoom("non_existent", "guest_user"))
                .isInstanceOf(ChatRoomNotFoundException.class)
                .hasMessage("Chat room 'non_existent' does not exist.");
    }

    // ==========================================
    // Test Case 5: Chat Room Deletion (Optional)
    // ==========================================

    @Test
    @DisplayName("Test Case 5: Delete chat room removes all associated Redis keys")
    void testDeleteChatRoom_Success() {
        when(setOperations.isMember(ChatServiceImpl.KEY_ALL_ROOMS, "general")).thenReturn(true);

        ApiResponse response = chatService.deleteChatRoom("general");

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getMessage()).contains("deleted successfully");

        // Verify keys deleted
        verify(redisTemplate).delete(List.of("chatroom:general:meta", "chatroom:general:participants", "chatroom:general:messages"));
        // Verify removed from active rooms Set
        verify(setOperations).remove(ChatServiceImpl.KEY_ALL_ROOMS, "general");
    }

    // ==========================================
    // Helper Tests: Listing Rooms and Participants
    // ==========================================

    @Test
    @DisplayName("Test retrieving all chat rooms with metadata and participant count")
    void testGetAllChatRooms() {
        when(setOperations.members(ChatServiceImpl.KEY_ALL_ROOMS)).thenReturn(Set.of("general"));
        Map<Object, Object> metaMap = new HashMap<>();
        metaMap.put("roomId", "general");
        metaMap.put("roomName", "general");
        metaMap.put("createdAt", "2024-01-01T10:00:00Z");

        when(hashOperations.entries("chatroom:general:meta")).thenReturn(metaMap);
        when(setOperations.size("chatroom:general:participants")).thenReturn(5L);

        List<ChatRoomMetaDto> rooms = chatService.getAllChatRooms();

        assertThat(rooms).hasSize(1);
        assertThat(rooms.get(0).getRoomId()).isEqualTo("general");
        assertThat(rooms.get(0).getParticipantCount()).isEqualTo(5L);
    }

    @Test
    @DisplayName("Test retrieving participants for a chat room")
    void testGetParticipants() {
        when(setOperations.isMember(ChatServiceImpl.KEY_ALL_ROOMS, "general")).thenReturn(true);
        when(setOperations.members("chatroom:general:participants")).thenReturn(Set.of("alice", "bob"));

        Set<String> participants = chatService.getParticipants("general");

        assertThat(participants).containsExactlyInAnyOrder("alice", "bob");
    }
}
