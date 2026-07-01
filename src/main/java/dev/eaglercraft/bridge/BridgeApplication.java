package dev.eaglercraft.bridge;

import dev.eaglercraft.network.EaglerWebSocketBridgeServer;

public final class BridgeApplication {
    public static void main(String[] args) throws Exception {
        BridgeConfig config = BridgeConfig.fromArgs(args);
        EaglerWebSocketBridgeServer server = new EaglerWebSocketBridgeServer(config);
        server.start();
        Thread.currentThread().join();
    }
}
