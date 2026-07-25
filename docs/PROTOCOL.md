# BB-8 BLE Protocol (Sphero V1)

Ported from [spherov2.py](https://github.com/artificial-intelligence-class/spherov2.py).

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

## Key Commands (DID=0x02)

| CID | Name | Data |
|-----|------|------|
| 0x01 | set_heading | heading (2 bytes BE) |
| 0x02 | set_stabilization | 0/1 |
| 0x20 | set_main_led | R, G, B |
| 0x30 | roll | speed, heading (2B BE), mode, reverse |

Roll modes: STOP=0, GO=1, CALIBRATE=2
