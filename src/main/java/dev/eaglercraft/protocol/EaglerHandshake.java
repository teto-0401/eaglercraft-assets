package dev.eaglercraft.protocol;

import dev.eaglercraft.util.ByteBuf;

import java.util.ArrayList;
import java.util.List;

public final class EaglerHandshake {
    public static final int MIN_EAGLER_PROTOCOL = 3;
    public static final int SELECTED_EAGLER_PROTOCOL = 5;

    public record ClientHandshake(List<Integer> advertisedProtocols, int minecraftProtocol, String clientBrand,
                                  String clientVersion, int flags, String username) {}

    public ClientHandshake parseClientHandshake(byte[] bytes) {
        ByteBuf in = new ByteBuf(bytes);
        int id = in.readUnsignedByte();
        if (id != EaglerPacketIds.CLIENT_HANDSHAKE) throw new IllegalArgumentException("Expected Eagler 0x01, got 0x" + Integer.toHexString(id));
        int marker = in.readUnsignedByte();
        List<Integer> protocols = new ArrayList<>();
        for (int i = 0; i < 5 && in.remaining() >= 2; i++) protocols.add(in.readUnsignedShort());
        int mcProtocol = in.readUnsignedShort();
        String brand = in.readString8();
        String version = in.readString8();
        int flags = in.readInt();
        String username = in.readString8();
        if (marker != 2) throw new IllegalArgumentException("Unsupported client handshake marker: " + marker);
        return new ClientHandshake(protocols, mcProtocol, brand, version, flags, username);
    }

    public byte[] serverHandshake(int minecraftProtocol) {
        return new ByteBuf()
                .writeByte(EaglerPacketIds.SERVER_HANDSHAKE)
                .writeShort(SELECTED_EAGLER_PROTOCOL)
                .writeShort(minecraftProtocol)
                .writeString8("EaglercraftBridge")
                .writeString8("prototype")
                .writeByte(0) // no auth required
                .writeShort(0) // no auth payload
                .toByteArray();
    }

    public byte[] protocolMismatch(int minecraftProtocol) {
        return new ByteBuf()
                .writeByte(EaglerPacketIds.SERVER_PROTOCOL_MISMATCH)
                .writeShort(3).writeShort(3).writeShort(4).writeShort(5)
                .writeShort(1).writeShort(minecraftProtocol)
                .toByteArray();
    }
}
