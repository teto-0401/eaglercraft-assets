package dev.eaglercraft.util;

public final class PacketLog {
    private PacketLog() {}
    public static int packetId(byte[] packet) {
        try { return new ByteBuf(packet).readVarInt(); } catch (RuntimeException ex) { return -1; }
    }
    public static String hexId(int id) { return id < 0 ? "unknown" : "0x" + Integer.toHexString(id); }
}
