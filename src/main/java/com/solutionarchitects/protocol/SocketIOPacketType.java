package com.solutionarchitects.protocol;

public enum SocketIOPacketType {



    Open(0), Close(1), Ping(2), Pong(3), Message(4), Upgrade(5), NoOp(6), Invalid(-1);


    public static final SocketIOPacketType[] VALUES = values();
    private final int value;

    SocketIOPacketType(int value) {
        this.value=value;
    }



    public int getValue() {
        return value;
    }

    public static SocketIOPacketType parsePacketType(int value) {
        for (SocketIOPacketType type : VALUES) {
            if (type.getValue() == value) {
                return type;
            }
        }

        return Invalid;
    }


}
