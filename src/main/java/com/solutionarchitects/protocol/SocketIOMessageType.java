package com.solutionarchitects.protocol;

public enum SocketIOMessageType {





    Invalid(-1),

    Connect(0), DisConnect(1), Event(2), Ack(3), Error(4), BinaryEvent(5);

    public static final SocketIOMessageType[] VALUES = values();
    private final int value;

    SocketIOMessageType(int value) {
        this.value=value;
    }



    public int getValue() {
        return value;
    }

    public static SocketIOMessageType parseMessageType(int value) {
        for (SocketIOMessageType type : VALUES) {
            if (type.getValue() == value) {
                return type;
            }
        }

        return Invalid;
    }



}
