# Rostrum Remediation Tracks Status

This document records the current implementation status for the three active remediation tracks.

## Tracks

1. Terminal
2. Server
3. Misc UI

Do not add new tracks without an explicit product decision.

## Server

Current implementation:

- `FileSystemBackend` is the product-level file boundary.
- `RemoteFileEditor` implements `FileSystemBackend` as `REMOTE_SERVER`.
- `ActiveFileSystemManager` now keeps both:
  - a compatibility `FileSystemService` fallback for legacy SFTP callers;
  - an active `FileSystemBackend` for new UI, preview, editor, and file operations.
- When `rostrum-server` is healthy through the SSH tunnel, the active backend switches to `REMOTE_SERVER`.
- When server startup, tunnel, or health check fails, the active backend remains SFTP and exposes fallback state.

Quality bar:

- Server must be a preferred remote substrate, not a parallel hidden client.
- SFTP fallback must remain explicit and visible.
- UI must expose whether actions target local, SFTP, or rostrum-server.

## Terminal

Current implementation:

- `TerminalBackend` is the product-level byte-stream terminal boundary.
- `SshTerminalBackendAdapter` adapts existing `SshTerminalSession` to `TerminalBackend`.
- `SshTerminalSession` now exposes raw byte output in addition to legacy string output.
- Remote workspace UI exposes terminal backend kind and state.

Quality bar:

- Real terminal rendering must consume byte streams, not accumulated strings.
- SSH PTY, local PTY, and future rostrum-server WebSocket PTY should share the same boundary.
- xterm.js/WebView work should build on `TerminalBackend` rather than on `IShellSession`.

## Misc UI

Current implementation:

- Remote workspace header shows active filesystem target and backend status.
- File preview, editor, realtime editor, and remote preview plugins use `FileSystemBackend`.
- Backend state is visible enough to distinguish Server primary path from SFTP fallback.

Quality bar:

- User-visible actions must clearly target the current workspace/backend.
- Workspace isolation should move from global state toward workspace-scoped state managers.
- Host/session/server degradation must be visible before adding more tooling.
