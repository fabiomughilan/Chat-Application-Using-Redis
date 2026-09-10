package com.freightfox.chatapp.service;

import com.freightfox.chatapp.dto.response.ApiResponse;
import com.freightfox.chatapp.dto.response.ChatHistoryResponse;
import com.freightfox.chatapp.dto.response.ChatRoomMetaDto;

import java.util.List;
import java.util.Set;

public interface ChatService {

    ApiResponse createChatRoom(String roomName);

    ApiResponse joinChatRoom(String roomId, String participant);

    ApiResponse sendMessage(String roomId, String participant, String message);

    ChatHistoryResponse getChatHistory(String roomId, int limit);

    ApiResponse deleteChatRoom(String roomId);

    List<ChatRoomMetaDto> getAllChatRooms();

    Set<String> getParticipants(String roomId);
}
