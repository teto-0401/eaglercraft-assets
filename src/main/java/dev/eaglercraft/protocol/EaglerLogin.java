package dev.eaglercraft.protocol;

import dev.eaglercraft.util.ByteBuf;

public final class EaglerLogin {
    public record ClientLoginStart(String username) {}

    public ClientLoginStart parseClientLoginStart(byte[] bytes, int selectedEaglerProtocol) {
        ByteBuf in = new ByteBuf(bytes);
        int id = in.readUnsignedByte();
        if (id != EaglerPacketIds.CLIENT_LOGIN_START) throw new IllegalArgumentException("Expected Eagler 0x04, got 0x" + Integer.toHexString(id));
        String username = null;
        if (selectedEaglerProtocol >= 5) {
            int markerOrEmpty = in.readUnsignedByte();
            if (markerOrEmpty != 0 && in.remaining() > 0) username = "unknown";
        } else {
            username = in.readString8();
            in.readString8();
            in.readUnsignedByte();
        }
        return new ClientLoginStart(username);
    }

    public byte[] loginSuccess() {
        return new ByteBuf().writeByte(EaglerPacketIds.LOGIN_SUCCESS).toByteArray();
    }

    public byte[] loginDenied(String reason) {
        byte[] msg = reason.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return new ByteBuf().writeByte(EaglerPacketIds.LOGIN_DENIED).writeShort(msg.length).writeBytes(msg).toByteArray();
    }
}
