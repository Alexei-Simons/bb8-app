# Protocol References (read-only)

Cloned community reverse-engineering repos. Not shipped in the app.

| Repo | Purpose |
|------|---------|
| `spherov2.py` | Primary protocol reference (packet format, handshake, commands) |
| `spherov2.js` | TypeScript port, useful for cross-checking |
| `BB8Controller` | Legacy Android app (classic BT, not BLE — low value) |

Re-clone:

```bash
git clone --depth 1 https://github.com/artificial-intelligence-class/spherov2.py.git spherov2.py
git clone --depth 1 https://github.com/igbopie/spherov2.js.git spherov2.js
```
