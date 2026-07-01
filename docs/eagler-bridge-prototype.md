# Eaglercraft 26.1.2 Bridge Prototype

This project is a minimal Java 25/Gradle prototype bridge for Eaglercraft 26.1.2 clients.

## What it implements

- WebSocket server for browser clients.
- Eagler pre-Minecraft handshake:
  - client `0x01` ClientHandshake
  - server `0x02` ServerHandshake
- Minimal Eagler login:
  - client `0x04` ClientLoginStart
  - server `0x09` LoginSuccess
- TCP connection to a configured vanilla Minecraft server after `0x09`.
- Packet ID logging in both directions after relay begins.

## Not implemented yet

- Skins and capes.
- Cookies.
- Redirects.
- Authentication.
- Eagler plugin channels.
- Profile-data request packets (`0x05`) are not sent by this bridge; optional client profile packets (`0x07`, `0x08`) are ignored if received.

## Running

```bash
gradle run --args="--listenHost 0.0.0.0 --listenPort 8081 --minecraftHost 127.0.0.1 --minecraftPort 25565"
```

Optional:

```bash
--minecraftProtocol <number>
```

If omitted, the bridge echoes the Minecraft protocol version requested by the Eagler client in its `0x01` handshake.

## Relay framing assumption and translation point

The Eagler pre-Minecraft packets are one binary WebSocket frame per Eagler packet. After `0x09` LoginSuccess, this prototype assumes each binary WebSocket frame contains one uncompressed Minecraft packet payload **without** the TCP VarInt length prefix.

Minecraft TCP streams require each packet to be prefixed with a VarInt length. Therefore the relay performs this framing translation:

- WebSocket client -> TCP server: add a VarInt packet length before writing the payload to TCP.
- TCP server -> WebSocket client: read the VarInt packet length, then send the packet payload as one binary WebSocket frame.

If the actual Eaglercraft 26.1.2 runtime sends a different post-login framing, this is the place that must change: `dev.eaglercraft.network.MinecraftTcpRelay`.
