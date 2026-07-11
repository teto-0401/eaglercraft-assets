# eaglercraft-assets

This repository now supports a lightweight launcher flow for the browser client.

## Usage

Serve the repository over HTTP and open the root page. The page will show a small launcher card where you can enter a WebSocket bridge/proxy URL such as:

- ws://localhost:8081/
- ws://your-host:8081/

After pressing Start client, the runtime is loaded on demand instead of starting immediately.

## Notes

- The page still expects a compatible bridge or proxy for multiplayer-style connectivity.
- The launcher keeps the asset entrypoint in place, but defers runtime startup until the user chooses to begin.
