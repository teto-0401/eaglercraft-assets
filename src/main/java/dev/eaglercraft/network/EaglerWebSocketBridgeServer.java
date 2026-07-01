package dev.eaglercraft.network;

import dev.eaglercraft.bridge.BridgeConfig;
import dev.eaglercraft.protocol.EaglerHandshake;
import dev.eaglercraft.protocol.EaglerLogin;
import dev.eaglercraft.protocol.EaglerPacketIds;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class EaglerWebSocketBridgeServer implements WebSocketConnection.Listener, AutoCloseable {
    private enum State { WAIT_HANDSHAKE, WAIT_LOGIN, RELAYING }
    private record Session(State state, int minecraftProtocol, MinecraftTcpRelay relay) {}

    private final BridgeConfig config;
    private final EaglerHandshake handshake = new EaglerHandshake();
    private final EaglerLogin login = new EaglerLogin();
    private final Map<WebSocketConnection, Session> sessions = new ConcurrentHashMap<>();
    private ServerSocket serverSocket;

    public EaglerWebSocketBridgeServer(BridgeConfig config) { this.config = config; }

    public void start() throws IOException {
        serverSocket = new ServerSocket(config.listenPort(), 50, java.net.InetAddress.getByName(config.listenHost()));
        System.out.printf("Eagler bridge listening on ws://%s:%d and forwarding to %s:%d%n",
                config.listenHost(), config.listenPort(), config.minecraftHost(), config.minecraftPort());
        Thread.ofVirtual().name("ws-acceptor").start(this::acceptLoop);
    }

    private void acceptLoop() {
        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                WebSocketConnection conn = new WebSocketConnection(socket, this);
                sessions.put(conn, new Session(State.WAIT_HANDSHAKE, -1, null));
                System.out.printf("WS open %s%n", conn.remoteAddress());
                conn.start();
            } catch (IOException ex) {
                if (!serverSocket.isClosed()) System.err.println("Accept error: " + ex.getMessage());
            }
        }
    }

    @Override public void onBinary(WebSocketConnection conn, byte[] bytes) {
        Session s = sessions.get(conn);
        if (s == null) return;
        try {
            switch (s.state()) {
                case WAIT_HANDSHAKE -> handleHandshake(conn, bytes);
                case WAIT_LOGIN -> handleLogin(conn, bytes, s);
                case RELAYING -> s.relay().forwardClientPacket(bytes);
            }
        } catch (Exception ex) {
            System.err.println("Bridge error: " + ex.getMessage());
            conn.close();
        }
    }

    @Override public void onText(WebSocketConnection conn, String text) {
        System.out.printf("Ignoring text frame from %s: %s%n", conn.remoteAddress(), text);
    }

    @Override public void onClose(WebSocketConnection conn) {
        Session s = sessions.remove(conn);
        if (s != null && s.relay() != null) s.relay().close();
        System.out.printf("WS close %s%n", conn.remoteAddress());
    }

    @Override public void onError(WebSocketConnection conn, Exception ex) {
        System.err.println("WebSocket error from " + conn.remoteAddress() + ": " + ex.getMessage());
    }

    private void handleHandshake(WebSocketConnection conn, byte[] bytes) throws IOException {
        EaglerHandshake.ClientHandshake client = handshake.parseClientHandshake(bytes);
        System.out.printf("Eagler C->S 0x%02x username=%s mcProtocol=%d advertised=%s%n",
                EaglerPacketIds.CLIENT_HANDSHAKE, client.username(), client.minecraftProtocol(), client.advertisedProtocols());
        int mcProtocol = config.expectedMinecraftProtocol() > 0 ? config.expectedMinecraftProtocol() : client.minecraftProtocol();
        if (!client.advertisedProtocols().contains(EaglerHandshake.SELECTED_EAGLER_PROTOCOL)) {
            System.out.printf("Eagler S->C 0x%02x protocol mismatch%n", EaglerPacketIds.SERVER_PROTOCOL_MISMATCH);
            conn.sendBinary(handshake.protocolMismatch(mcProtocol));
            conn.close();
            return;
        }
        System.out.printf("Eagler S->C 0x%02x selectedEagler=%d mcProtocol=%d%n",
                EaglerPacketIds.SERVER_HANDSHAKE, EaglerHandshake.SELECTED_EAGLER_PROTOCOL, mcProtocol);
        conn.sendBinary(handshake.serverHandshake(mcProtocol));
        sessions.put(conn, new Session(State.WAIT_LOGIN, mcProtocol, null));
    }

    private void handleLogin(WebSocketConnection conn, byte[] bytes, Session s) throws IOException {
        int packetId = bytes.length == 0 ? -1 : bytes[0] & 0xff;
        System.out.printf("Eagler C->S 0x%02x login bytes=%d%n", packetId, bytes.length);
        if (packetId == EaglerPacketIds.CLIENT_PROFILE_DATA || packetId == EaglerPacketIds.CLIENT_PROFILE_DATA_DONE) {
            System.out.println("Ignoring optional profile packet in prototype");
            return;
        }
        if (packetId != EaglerPacketIds.CLIENT_LOGIN_START) {
            conn.sendBinary(login.loginDenied("Expected Eagler login packet 0x04"));
            conn.close();
            return;
        }
        EaglerLogin.ClientLoginStart client = login.parseClientLoginStart(bytes, EaglerHandshake.SELECTED_EAGLER_PROTOCOL);
        System.out.printf("Eagler login start username=%s%n", client.username());
        System.out.printf("Eagler S->C 0x%02x login success%n", EaglerPacketIds.LOGIN_SUCCESS);
        conn.sendBinary(login.loginSuccess());
        MinecraftTcpRelay relay = new MinecraftTcpRelay(conn, config.minecraftHost(), config.minecraftPort());
        sessions.put(conn, new Session(State.RELAYING, s.minecraftProtocol(), relay));
    }

    @Override public void close() throws IOException { if (serverSocket != null) serverSocket.close(); }
}
