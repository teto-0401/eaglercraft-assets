package dev.eaglercraft.network;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class WebSocketConnection implements AutoCloseable {
    public interface Listener {
        void onBinary(WebSocketConnection conn, byte[] payload);
        void onText(WebSocketConnection conn, String text);
        void onClose(WebSocketConnection conn);
        void onError(WebSocketConnection conn, Exception ex);
    }

    private static final String MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final Listener listener;
    private volatile boolean open = true;

    public WebSocketConnection(Socket socket, Listener listener) throws IOException {
        this.socket = socket;
        this.socket.setTcpNoDelay(true);
        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();
        this.listener = listener;
        handshake();
    }

    public String remoteAddress() { return String.valueOf(socket.getRemoteSocketAddress()); }

    public void start() { Thread.ofVirtual().name("ws-reader").start(this::readLoop); }

    public synchronized void sendBinary(byte[] payload) throws IOException { sendFrame(0x2, payload); }
    public synchronized void sendBinary(ByteBuffer payload) throws IOException { byte[] b = new byte[payload.remaining()]; payload.get(b); sendBinary(b); }
    public synchronized void sendText(String text) throws IOException { sendFrame(0x1, text.getBytes(StandardCharsets.UTF_8)); }

    private void readLoop() {
        try {
            while (open) {
                int b0 = in.read();
                if (b0 < 0) break;
                int b1 = readByte();
                int opcode = b0 & 0x0f;
                boolean masked = (b1 & 0x80) != 0;
                long len = b1 & 0x7f;
                if (len == 126) len = readUnsignedShort();
                else if (len == 127) len = readLong();
                byte[] mask = masked ? in.readNBytes(4) : new byte[0];
                byte[] payload = in.readNBytes(Math.toIntExact(len));
                if (payload.length != len) throw new EOFException("short websocket frame");
                if (masked) for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i & 3];
                switch (opcode) {
                    case 0x1 -> listener.onText(this, new String(payload, StandardCharsets.UTF_8));
                    case 0x2 -> listener.onBinary(this, payload);
                    case 0x8 -> { close(); return; }
                    case 0x9 -> sendFrame(0xA, payload);
                    case 0xA -> { }
                    default -> throw new IOException("Unsupported WebSocket opcode " + opcode);
                }
            }
        } catch (Exception ex) {
            if (open) listener.onError(this, ex);
        } finally {
            close();
            listener.onClose(this);
        }
    }

    private void handshake() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.ISO_8859_1));
        String requestLine = reader.readLine();
        if (requestLine == null || !requestLine.startsWith("GET ")) throw new IOException("Invalid WebSocket request");
        Map<String, String> headers = new LinkedHashMap<>();
        for (String line; (line = reader.readLine()) != null && !line.isEmpty();) {
            int idx = line.indexOf(':');
            if (idx > 0) headers.put(line.substring(0, idx).toLowerCase(Locale.ROOT), line.substring(idx + 1).trim());
        }
        String key = headers.get("sec-websocket-key");
        if (key == null) throw new IOException("Missing Sec-WebSocket-Key");
        String accept = Base64.getEncoder().encodeToString(sha1((key + MAGIC).getBytes(StandardCharsets.ISO_8859_1)));
        String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: " + accept + "\r\n\r\n";
        out.write(response.getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
    }

    private synchronized void sendFrame(int opcode, byte[] payload) throws IOException {
        if (!open) return;
        out.write(0x80 | opcode);
        if (payload.length < 126) out.write(payload.length);
        else if (payload.length <= 0xffff) { out.write(126); writeShort(payload.length); }
        else { out.write(127); writeLong(payload.length); }
        out.write(payload);
        out.flush();
    }

    private int readByte() throws IOException { int b = in.read(); if (b < 0) throw new EOFException(); return b; }
    private int readUnsignedShort() throws IOException { return (readByte() << 8) | readByte(); }
    private long readLong() throws IOException { long v = 0; for (int i = 0; i < 8; i++) v = (v << 8) | readByte(); return v; }
    private void writeShort(int v) throws IOException { out.write((v >>> 8) & 0xff); out.write(v & 0xff); }
    private void writeLong(long v) throws IOException { for (int i = 7; i >= 0; i--) out.write((int)(v >>> (8 * i)) & 0xff); }
    private static byte[] sha1(byte[] bytes) throws IOException { try { return MessageDigest.getInstance("SHA-1").digest(bytes); } catch (Exception e) { throw new IOException(e); } }

    @Override public void close() {
        open = false;
        try { socket.close(); } catch (IOException ignored) { }
    }
}
