# KFN architecture

The Android client uses VpnService permission checks. No TUN interface is created until a real data-plane implementation is available.

## Modules
- Core: validated endpoint metadata, engine contracts, lifecycle states and engine registry.
- Data plane: separate, auditable adapters for supported protocols; not included yet.
- Android service: foreground lifecycle, protected upstream sockets, TUN ownership and cancellation; planned.
- Policy: split tunneling, IPv4/IPv6 and DNS routing, fail-closed behavior; planned.
- Credentials: Android Keystore-backed storage; never put keys into public Git or logs.
- Adaptive selection: explicit user policy and measured connection health; planned.

## Acceptance gates
- Verify a real remote handshake and traffic forwarding before displaying Connected.
- Test DNS and IPv6 leaks, network handover and reconnect.
- Verify disconnect releases resources.
- Do not establish an empty default-route tunnel.
- Do not implement custom cryptography.
- No server credentials in the repository.

Current engine registry is empty intentionally. Protocol names describe planned adapters, not working VPN implementations.
