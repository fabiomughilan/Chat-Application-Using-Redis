package com.freightfox.chatapp.exception;

public class DuplicateChatRoomException extends RuntimeException {
    public DuplicateChatRoomException(String message) {
        super(message);
    }
}
