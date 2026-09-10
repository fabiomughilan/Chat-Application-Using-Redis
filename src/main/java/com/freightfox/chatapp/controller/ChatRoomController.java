package com.freightfox.chatapp.controller;

import com.freightfox.chatapp.dto.request.CreateRoomRequest;
import com.freightfox.chatapp.dto.request.JoinRoomRequest;
import com.freightfox.chatapp.dto.request.SendMessageRequest;
import com.freightfox.chatapp.dto.response.ApiResponse;
import com.freightfox.chatapp.dto.response.ChatHistoryResponse;
import com.freightfox.chatapp.dto.response.ChatRoomMetaDto;
import com.freightfox.chatapp.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/chatapp/chatrooms")
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatService chatService;

    /**
     * 1. Create a Chat Room
     * Endpoint: POST /api/chatapp/chatrooms
     */
    @PostMapping
    public ResponseEntity<ApiResponse> createChatRoom(@Valid @RequestBody CreateRoomRequest request) {
        ApiResponse response = chatService.createChatRoom(request.getRoomName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 2. Join a Chat Room
     * Endpoint: POST /api/chatapp/chatrooms/{roomId}/join
     */
    @PostMapping("/{roomId}/join")
    public ResponseEntity<ApiResponse> joinChatRoom(
            @PathVariable("roomId") String roomId,
            @Valid @RequestBody JoinRoomRequest request) {
        ApiResponse response = chatService.joinChatRoom(roomId, request.getParticipant());
        return ResponseEntity.ok(response);
    }

    /**
     * 3. Send a Message
     * Endpoint: POST /api/chatapp/chatrooms/{roomId}/messages
     */
    @PostMapping("/{roomId}/messages")
    public ResponseEntity<ApiResponse> sendMessage(
            @PathVariable("roomId") String roomId,
            @Valid @RequestBody SendMessageRequest request) {
        ApiResponse response = chatService.sendMessage(roomId, request.getParticipant(), request.getMessage());
        return ResponseEntity.ok(response);
    }

    /**
     * 4. Retrieve Chat History
     * Endpoint: GET /api/chatapp/chatrooms/{roomId}/messages?limit=10
     */
    @GetMapping("/{roomId}/messages")
    public ResponseEntity<ChatHistoryResponse> getChatHistory(
            @PathVariable("roomId") String roomId,
            @RequestParam(name = "limit", defaultValue = "10") int limit) {
        ChatHistoryResponse response = chatService.getChatHistory(roomId, limit);
        return ResponseEntity.ok(response);
    }

    /**
     * 5. (Optional) Delete a Chat Room
     * Endpoint: DELETE /api/chatapp/chatrooms/{roomId}
     */
    @DeleteMapping("/{roomId}")
    public ResponseEntity<ApiResponse> deleteChatRoom(@PathVariable("roomId") String roomId) {
        ApiResponse response = chatService.deleteChatRoom(roomId);
        return ResponseEntity.ok(response);
    }

    /**
     * Helper: List All Chat Rooms
     * Endpoint: GET /api/chatapp/chatrooms
     */
    @GetMapping
    public ResponseEntity<List<ChatRoomMetaDto>> getAllChatRooms() {
        List<ChatRoomMetaDto> rooms = chatService.getAllChatRooms();
        return ResponseEntity.ok(rooms);
    }

    /**
     * Helper: Get Participants of a Chat Room
     * Endpoint: GET /api/chatapp/chatrooms/{roomId}/participants
     */
    @GetMapping("/{roomId}/participants")
    public ResponseEntity<Set<String>> getParticipants(@PathVariable("roomId") String roomId) {
        Set<String> participants = chatService.getParticipants(roomId);
        return ResponseEntity.ok(participants);
    }
}
