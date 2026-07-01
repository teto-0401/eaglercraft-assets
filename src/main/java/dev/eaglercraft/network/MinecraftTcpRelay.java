package dev.eaglercraft.network;

import dev.eaglercraft.util.ByteBuf;
import dev.eaglercraft.util.PacketLog;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MinecraftTcpRelay implements AutoCloseable {
    private final WebSocketConnection webSocket;
    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread serverReader;

    public MinecraftTcpRelay(WebSocketConnection webSocket, String host, int port) throws IOException {
        this.webSocket = webSocket;
        this.socket = new Socket(host, port);
        this.socket.setTcpNoDelay(true);
        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();
        this.serverReader = Thread.ofVirtual().name("minecraft-tcp-to-ws").start(this::pumpServerToClient);
    }

    public void forwardClientPacket(byte[] packetPayload) throws IOException {
        int id = PacketLog.packetId(packetPayload);
        System.out.printf("MC C->S packet %s bytes=%d%n", PacketLog.hexId(id), packetPayload.length);
        byte[] len = new ByteBuf().writeVarInt(packetPayload.length).toByteArray();
        synchronized (out) {
            out.write(len);
            out.write(packetPayload);
            out.flush();
        }
    }

    private void pumpServerToClient() {
        try {
            while (running.get()) {
                int len = readVarInt(in);
                byte[] packet = in.readNBytes(len);
                if (packet.length != len) throw new EOFException("short Minecraft packet");
                int id = PacketLog.packetId(packet);
                System.out.printf("MC S->C packet %s bytes=%d%n", PacketLog.hexId(id), packet.length);
                webSocket.sendBinary(ByteBuffer.wrap(packet));
            }
        } catch (IOException ex) {
            if (running.get()) System.err.println("TCP relay stopped: " + ex.getMessage());
            webSocket.close();
        } finally {
            close();
        }
    }

    private static int readVarInt(InputStream in) throws IOException {
        int value = 0;
        int shift = 0;
        while (shift < 35) {
            int b = in.read();
            if (b < 0) throw new EOFException("EOF reading VarInt");
            value |= (b & 0x7f) << shift;
            if ((b & 0x80) == 0) return value;
            shift += 7;
        }
        throw new IOException("VarInt too large");
    }

    @Override public void close() {
        if (!running.getAndSet(false)) return;
        try { socket.close(); } catch (IOException ignored) { }
    }
}
