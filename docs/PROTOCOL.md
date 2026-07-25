# BB-8 BLE Protocol (Sphero V1)

Ported from [spherov2.py](https://github.com/artificial-intelligence-class/spherov2.py) and Sphero API 1.20.

## Connection

1. Scan for BLE devices with name prefix `BB-`
2. Connect (no OS pairing required)
3. Handshake:
   - `2bbd` Anti-DOS: write `011i3`
   - `2bb2` TX Power: write `[7]`
4. Subscribe to notifications on `2ba6` (response)
5. Wake: write `[1]` to `2bbf` if sleeping

## GATT UUIDs

| Char | UUID suffix | Purpose |
|------|-------------|---------|
| Command | `2ba1` | Send packets (20-byte chunks) |
| Response | `2ba6` | Notifications |
| Anti-DOS | `2bbd` | Unlock |
| TX Power | `2bb2` | Power level |
| Wake | `2bbf` | Wake CPU |

Full UUID: `22bb746f-{suffix}-7554-2d6f-726568705327`

## Packet Format

```
[SOP1=0xFF][SOP2=0xFF][DID][CID][SEQ][DLEN][data...][CHK]
CHK = 0xFF - (sum(DID..data) & 0xFF)
```

Async notifications use `SOP2=0xFE`:

```
[0xFF][0xFE][ID_CODE][DLEN_MSB][DLEN_LSB][data...][CHK]
```

## Key Commands (DID=0x02)

| CID | Name | Data |
|-----|------|------|
| 0x01 | set_heading | heading (2 bytes BE) |
| 0x02 | set_stabilization | 0/1 |
| 0x11 | set_data_streaming | 13-byte payload (see below) |
| 0x13 | configure_locator | flags, x, y, yaw_tare (int16 BE each) |
| 0x20 | set_main_led | R, G, B |
| 0x30 | roll | speed, heading (2B BE), mode, reverse |
| 0x50 | run_macro | macro_id u8 |
| 0x51 | save_temp_macro | 0xFF, flags, bytecode... |
| 0x54 | init_macro_executive | (empty) |
| 0x55 | abort_macro | (empty) |

Roll modes: STOP=0, GO=1, CALIBRATE=2

## Sensor streaming (SET_DATA_STREAMING)

13-byte payload (all multi-byte fields big-endian):

| Offset | Field |
|--------|-------|
| 0-1 | interval (400 Hz / interval = sample rate) |
| 2-3 | samples per packet |
| 4-7 | primary mask |
| 8 | packet count (0 = unlimited) |
| 9-12 | extended mask |

Locator lives in **extended** mask (not primary):

- `ODOM_X`: `0x08000000`
- `ODOM_Y`: `0x04000000`
- `VELOCITY_X`: `0x01000000`
- `VELOCITY_Y`: `0x00800000`

Async sensor data arrives as `ID_CODE=0x03` with signed int16 BE samples in mask bit order (MSB first). Locator values are cm; velocity is raw * 0.1 cm/s.

Call `configure_locator` before enabling streaming. BB-8 names start with `BB-` and do **not** swap locator axes (Ollie `2B-*` droids do).

## Macros

Host-side playback sends roll/LED/delay commands on a timer. Device macros compile to bytecode (opcodes `0x05` roll, `0x07` rgb, `0x0B` delay, `0x00` end), upload via `save_temp_macro`, then `run_macro(0xFF)`.

Call `init_macro_executive` once per session before macro upload.

## References

- `vendor/spherov2.py` (clone via `vendor/README.md`)
- [Sphero API 1.20 PDF](https://docs.gosphero.com/api/Sphero_API_1.20.pdf)
- [node-sphero-pwn-macros](https://github.com/pwnall/node-sphero-pwn-macros) (macro opcodes)
