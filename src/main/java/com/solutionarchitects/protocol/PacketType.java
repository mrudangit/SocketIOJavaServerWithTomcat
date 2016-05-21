package com.solutionarchitects.protocol;

public enum  PacketType {



    Open(0), Close(1), Ping(2), Pong(3), Message(4), Upgrade(5), NoOp(6), Invalid(-1),

    Connect(0, true), DisConnect(1, true), Event(2, true), Ack(3, true), Error(4, true), BinaryEvent(5, true);

    public static final PacketType[] VALUES = values();
    private final int value;
    private final boolean inner;

    PacketType(int value) {
        this(value, false);
    }

    PacketType(int value, boolean inner) {
        this.value = value;
        this.inner = inner;
    }

    public int getValue() {
        return value;
    }

    public static PacketType parsePacketType(int value) {
        for (PacketType type : VALUES) {
            if (type.getValue() == value && !type.inner) {
                return type;
            }
        }

        return Invalid;
    }

    public static PacketType parseSubPacketType(int value) {
        for (PacketType type : VALUES) {
            if (type.getValue() == value && type.inner) {
                return type;
            }
        }
        return Invalid;
    }

}
