package dev.eaglercraft.util;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class ByteBuf {
    private final byte[] in;
    private int pos;
    private final ByteArrayOutputStream out;

    public ByteBuf(byte[] input) { this.in = input; this.out = null; }
    public ByteBuf() { this.in = null; this.out = new ByteArrayOutputStream(); }

    public int remaining() { return in.length - pos; }
    public int readUnsignedByte() { require(1); return in[pos++] & 0xff; }
    public boolean readBoolean() { return readUnsignedByte() != 0; }
    public int readUnsignedShort() { return (readUnsignedByte() << 8) | readUnsignedByte(); }
    public int readInt() { return (readUnsignedByte() << 24) | (readUnsignedByte() << 16) | (readUnsignedByte() << 8) | readUnsignedByte(); }
    public int readVarInt() {
        int value = 0;
        int shift = 0;
        while (shift < 35) {
            int b = readUnsignedByte();
            value |= (b & 0x7f) << shift;
            if ((b & 0x80) == 0) return value;
            shift += 7;
        }
        throw new IllegalArgumentException("VarInt too large");
    }
    public byte[] readBytes(int len) { require(len); byte[] b = Arrays.copyOfRange(in, pos, pos + len); pos += len; return b; }
    public String readString8() { return new String(readBytes(readUnsignedByte()), StandardCharsets.UTF_8); }
    public String readString16() { return new String(readBytes(readUnsignedShort()), StandardCharsets.UTF_8); }
    public void skip(int len) { require(len); pos += len; }

    public ByteBuf writeByte(int v) { out.write(v & 0xff); return this; }
    public ByteBuf writeBoolean(boolean v) { return writeByte(v ? 1 : 0); }
    public ByteBuf writeShort(int v) { writeByte(v >>> 8); writeByte(v); return this; }
    public ByteBuf writeInt(int v) { writeByte(v >>> 24); writeByte(v >>> 16); writeByte(v >>> 8); writeByte(v); return this; }
    public ByteBuf writeVarInt(int value) { while ((value & ~0x7f) != 0) { writeByte((value & 0x7f) | 0x80); value >>>= 7; } return writeByte(value); }
    public ByteBuf writeBytes(byte[] b) { out.writeBytes(b); return this; }
    public ByteBuf writeString8(String s) { byte[] b = s.getBytes(StandardCharsets.UTF_8); if (b.length > 255) throw new IllegalArgumentException("string too long"); return writeByte(b.length).writeBytes(b); }
    public ByteBuf writeString16(String s) { byte[] b = s.getBytes(StandardCharsets.UTF_8); if (b.length > 65535) throw new IllegalArgumentException("string too long"); return writeShort(b.length).writeBytes(b); }
    public byte[] toByteArray() { return out.toByteArray(); }

    private void require(int len) { if (in == null || len < 0 || pos + len > in.length) throw new IllegalArgumentException("Unexpected end of packet"); }
}
