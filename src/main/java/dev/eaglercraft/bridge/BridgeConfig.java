package dev.eaglercraft.bridge;

public record BridgeConfig(String listenHost, int listenPort, String minecraftHost, int minecraftPort, int expectedMinecraftProtocol) {
    public static BridgeConfig fromArgs(String[] args) {
        String listenHost = get(args, "--listenHost", "0.0.0.0");
        int listenPort = Integer.parseInt(get(args, "--listenPort", "8081"));
        String mcHost = get(args, "--minecraftHost", "127.0.0.1");
        int mcPort = Integer.parseInt(get(args, "--minecraftPort", "25565"));
        int protocol = Integer.parseInt(get(args, "--minecraftProtocol", "0"));
        return new BridgeConfig(listenHost, listenPort, mcHost, mcPort, protocol);
    }
    private static String get(String[] args, String key, String def) {
        for (int i = 0; i < args.length - 1; i++) if (args[i].equals(key)) return args[i + 1];
        return def;
    }
}
