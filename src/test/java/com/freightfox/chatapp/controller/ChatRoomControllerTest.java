package com.freightfox.chatapp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.freightfox.chatapp.dto.request.CreateRoomRequest;
import com.freightfox.chatapp.dto.request.JoinRoomRequest;
import com.freightfox.chatapp.dto.request.SendMessageRequest;
import com.freightfox.chatapp.dto.response.ApiResponse;
import com.freightfox.chatapp.dto.response.ChatHistoryResponse;
import com.freightfox.chatapp.dto.response.ChatMessageDto;
import com.freightfox.chatapp.exception.ChatRoomNotFoundException;
import com.freightfox.chatapp.exception.DuplicateChatRoomException;
import com.freightfox.chatapp.service.ChatService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatRoomController.class)
class ChatRoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ChatService chatService;

    @Test
    @DisplayName("POST /api/chatapp/chatrooms - Should create chat room successfully")
    void testCreateChatRoom_Success() throws Exception {
        CreateRoomRequest request = new CreateRoomRequest("general");
        ApiResponse expectedResponse = ApiResponse.builder()
                .message("Chat room 'general' created successfully.")
                .roomId("general")
                .status("success")
                .build();

        when(chatService.createChatRoom("general")).thenReturn(expectedResponse);

        mockMvc.perform(post("/api/chatapp/chatrooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Chat room 'general' created successfully."))
                .andExpect(jsonPath("$.roomId").value("general"))
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    @DisplayName("POST /api/chatapp/chatrooms - Duplicate room returns 409 Conflict")
    void testCreateChatRoom_Duplicate() throws Exception {
        CreateRoomRequest request = new CreateRoomRequest("general");

        when(chatService.createChatRoom("general"))
                .thenThrow(new DuplicateChatRoomException("Chat room 'general' already exists."));

        mockMvc.perform(post("/api/chatapp/chatrooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Chat room 'general' already exists."));
    }

    @Test
    @DisplayName("POST /api/chatapp/chatrooms - Invalid blank name returns 400 Bad Request")
    void testCreateChatRoom_Invalid() throws Exception {
        CreateRoomRequest request = new CreateRoomRequest("");

        mockMvc.perform(post("/api/chatapp/chatrooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    @DisplayName("POST /api/chatapp/chatrooms/{roomId}/join - Should join room successfully")
    void testJoinChatRoom_Success() throws Exception {
        JoinRoomRequest request = new JoinRoomRequest("guest_user");
        ApiResponse expectedResponse = ApiResponse.builder()
                .message("User 'guest_user' joined chat room 'general'.")
                .status("success")
                .build();

        when(chatService.joinChatRoom("general", "guest_user")).thenReturn(expectedResponse);

        mockMvc.perform(post("/api/chatapp/chatrooms/general/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User 'guest_user' joined chat room 'general'."))
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    @DisplayName("POST /api/chatapp/chatrooms/{roomId}/join - Non-existent room returns 404 Not Found")
    void testJoinChatRoom_NotFound() throws Exception {
        JoinRoomRequest request = new JoinRoomRequest("guest_user");

        when(chatService.joinChatRoom("nonexistent", "guest_user"))
                .thenThrow(new ChatRoomNotFoundException("Chat room 'nonexistent' does not exist."));

        mockMvc.perform(post("/api/chatapp/chatrooms/nonexistent/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Chat room 'nonexistent' does not exist."));
    }

    @Test
    @DisplayName("POST /api/chatapp/chatrooms/{roomId}/messages - Should send message successfully")
    void testSendMessage_Success() throws Exception {
        SendMessageRequest request = new SendMessageRequest("guest_user", "Hello, everyone!");
        ApiResponse expectedResponse = ApiResponse.builder()
                .message("Message sent successfully.")
                .status("success")
                .build();

        when(chatService.sendMessage("general", "guest_user", "Hello, everyone!")).thenReturn(expectedResponse);

        mockMvc.perform(post("/api/chatapp/chatrooms/general/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Message sent successfully."))
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    @DisplayName("POST /api/chatapp/chatrooms/{roomId}/messages - Non-existent room returns 404 Not Found")
    void testSendMessage_NotFound() throws Exception {
        SendMessageRequest request = new SendMessageRequest("guest_user", "Hello, everyone!");

        when(chatService.sendMessage("nonexistent", "guest_user", "Hello, everyone!"))
                .thenThrow(new ChatRoomNotFoundException("Chat room 'nonexistent' does not exist."));

        mockMvc.perform(post("/api/chatapp/chatrooms/nonexistent/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Chat room 'nonexistent' does not exist."));
    }

    @Test
    @DisplayName("GET /api/chatapp/chatrooms/{roomId}/messages?limit=10 - Should retrieve chat history")
    void testGetChatHistory_Success() throws Exception {
        List<ChatMessageDto> messages = List.of(
                new ChatMessageDto("guest_user", "Hello, everyone!", "2024-01-01T10:00:00Z"),
                new ChatMessageDto("another_user", "Hi, guest_user!", "2024-01-01T10:01:00Z")
        );
        ChatHistoryResponse response = new ChatHistoryResponse(messages);

        when(chatService.getChatHistory(eq("general"), anyInt())).thenReturn(response);

        mockMvc.perform(get("/api/chatapp/chatrooms/general/messages")
                        .param("limit", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages").isArray())
                .andExpect(jsonPath("$.messages[0].participant").value("guest_user"))
                .andExpect(jsonPath("$.messages[0].message").value("Hello, everyone!"))
                .andExpect(jsonPath("$.messages[0].timestamp").value("2024-01-01T10:00:00Z"))
                .andExpect(jsonPath("$.messages[1].participant").value("another_user"))
                .andExpect(jsonPath("$.messages[1].message").value("Hi, guest_user!"))
                .andExpect(jsonPath("$.messages[1].timestamp").value("2024-01-01T10:01:00Z"));
    }

    @Test
    @DisplayName("DELETE /api/chatapp/chatrooms/{roomId} - Should delete chat room successfully")
    void testDeleteChatRoom_Success() throws Exception {
        ApiResponse expectedResponse = ApiResponse.builder()
                .message("Chat room 'general' deleted successfully.")
                .status("success")
                .build();

        when(chatService.deleteChatRoom("general")).thenReturn(expectedResponse);

        mockMvc.perform(delete("/api/chatapp/chatrooms/general"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Chat room 'general' deleted successfully."))
                .andExpect(jsonPath("$.status").value("success"));
    }
}
