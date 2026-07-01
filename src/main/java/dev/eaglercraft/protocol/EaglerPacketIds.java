package dev.eaglercraft.protocol;

public final class EaglerPacketIds {
    private EaglerPacketIds() {}
    public static final int CLIENT_HANDSHAKE = 0x01;
    public static final int SERVER_HANDSHAKE = 0x02;
    public static final int SERVER_PROTOCOL_MISMATCH = 0x03;
    public static final int CLIENT_LOGIN_START = 0x04;
    public static final int LOGIN_PROFILE_DATA_REQUEST = 0x05;
    public static final int SERVER_REDIRECT = 0x06;
    public static final int CLIENT_PROFILE_DATA = 0x07;
    public static final int CLIENT_PROFILE_DATA_DONE = 0x08;
    public static final int LOGIN_SUCCESS = 0x09;
    public static final int LOGIN_DENIED = 0x0a;
    public static final int ERROR = 0xff;
}
