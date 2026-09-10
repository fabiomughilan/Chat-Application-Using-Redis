package com.freightfox.chatapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoomMetaDto {

    private String roomId;
    private String roomName;
    private String createdAt;
    private long participantCount;
}
