package com.freightfox.chatapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JoinRoomRequest {

    @NotBlank(message = "Participant name cannot be empty or null")
    @Size(min = 1, max = 50, message = "Participant name must be between 1 and 50 characters")
    private String participant;
}
