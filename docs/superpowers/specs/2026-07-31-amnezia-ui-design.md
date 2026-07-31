# Amnezia-style UI for TgWsProxy Android

Date: 2026-07-31  
Status: draft for user review  
Repo: [MushroomSquad/tg-ws-proxy-android](https://github.com/MushroomSquad/tg-ws-proxy-android)  
Visual reference: [amnezia-vpn/amnezia-client](https://github.com/amnezia-vpn/amnezia-client) (Qt/QML tokens & Home layout — adapted to Jetpack Compose)

## Goal

Reskin the main Android app UI to Amnezia’s dark visual language: large circular Connect CTA, bottom Services drawer, bottom tab bar. Preserve existing proxy + ByeDPI VPN behavior with independent include toggles.

## Decisions (approved)

| Topic | Choice |
|-------|--------|
| Approach | Amnezia Home 1:1 (colors, Connect ring, drawer, tabs) |
| Dual services | **B**: one Connect starts/stops services marked “Include”; toggles live in drawer |
| Theme | Dark-only Amnezia palette |
| ByeDPI prefs UI | Keep existing PreferenceActivity; deep-link only (no XML reskin this release) |
| Fonts/assets | Do not copy Amnezia proprietary font/icons; use system sans + Material icons styled to palette |

## Visual tokens

| Token | Hex | Use |
|-------|-----|-----|
| bg | `#0E0E11` | Screen background |
| surface | `#1C1D21` | Tab bar, drawer, cards |
| border | `#2C2D30` | Dividers, strokes |
| text | `#D7D8DB` | Primary text, idle Connect ring |
| muted | `#878B91` | Secondary / meta |
| accent | `#FBB26A` | Connected, selected tab |
| error | `#EB5757` | Errors |
| buttonFill | `#D7D8DB` | Primary filled buttons |
| buttonOn | `#0E0E11` | Text on filled buttons |

- Corner radius: **16dp** (cards, sheets, primary buttons)
- Content padding: **16–24dp**
- Type scale (approx Amnezia): display 32/700, title 25/700, body 16/400, button 16/600, label 13/400 muted
- Connect ring: **~190dp**, stroke ~3dp

## Information architecture

Bottom tabs:

1. **Home** — Connect + Services drawer peek/expand  
2. **Settings** — proxy/CF/app/ByeDPI link  
3. **Logs** — optional log viewer + clear/export/share  

## Home

```
[ optional update / debug chips ]

        circular CONNECT / STOP
        status: Proxy · VPN · both / off

[ Services drawer peek ]
  handle + title + one-line status

[ Home | Settings | Logs ]
```

### Connect behavior

- Reads “Include in Connect” for Proxy and VPN from prefs.
- **Start**: start each included service that is not already running (VPN may require system permission first).
- **Stop**: stop each included service that is running.  
  Clarification for partial state: if a service is running but its Include switch is off, Stop still stops running included ones; orphan running service with Include off is stopped on next Connect cycle or via explicit Stop-all when pressing Stop while any included service runs — see Edge cases.
- **Disabled** when both Include switches are off; hint points user to Services drawer.
- Visual states: Disconnected (paleGray), Connecting (indeterminate arc), Connected/partial (apricot + status line).

### Services drawer

**Collapsed:** handle, “Services”, status line (`Proxy on · VPN off` / `Both on` / `All off`).

**Expanded:**

1. Telegram proxy — Include switch, Running/Stopped, Open in Telegram, Copy link, port/secret summary  
2. ByeDPI VPN — Include switch, Running/Stopped, open ByeDPI settings  
3. Quick actions — Battery tip, Update domain list (CF)  
4. Hint: Connect only starts what is included here  

Include switches do **not** immediately start/stop; only Connect/Stop does (except deep-link actions).

## Settings tab

Cards (surface, radius 16):

1. **Proxy** — port, secret show/copy, DC IP map, Save  
2. **Cloudflare** — short explanation, Update domain list, last status  
3. **App** — battery optimization, show first-run tip, version, update if available  
4. **ByeDPI** — open existing settings activity  

Save button: filled `#D7D8DB` / text `#0E0E11`, height 56, radius 16. If proxy is running, either require Stop first or apply-on-restart with explicit copy (prefer apply-on-restart with snackbar).

## Logs tab

- Show logs switch (default off)  
- Monospace log list on bg  
- Clear / Export / Share  
- Scroll-to-top when list is long  

## Motion

- Connect: color crossfade; connecting = ~1s rotating arc  
- Drawer expand/collapse  
- Respect reduce-motion: no spin, state via color/text only  

## Edge cases

| Case | Behavior |
|------|----------|
| VPN permission denied | Error snackbar; still start proxy if Include proxy on |
| One service fails | Partial connected status; accent stays apricot; error in status line |
| Both Include off | Connect disabled |
| First-run | Dialog restyled to dark Amnezia |
| Update available | Dialog; Download = primary light pill |
| Back | Collapse drawer on Home; Settings/Logs → Home |
| Include off while service running | Service keeps running until user presses Stop (Stop stops currently running services that are still Included **or** any running service when user intends full stop — implement as: Stop stops all currently running managed services) |

**Stop semantics (resolved):** pressing Stop stops **all running** managed services (proxy and/or VPN), regardless of Include. Include only gates **Start**.

## Out of scope

- Reskin ByeDPI Preference XML screens  
- Light theme  
- Copying Amnezia PT Root UI font or proprietary SVG assets  
- Strategy picker / ByeByeDPI proxy test  
- Desktop / non-Android  

## Implementation sketch (Compose)

- `Theme.kt` → Amnezia dark ColorScheme + typography helpers  
- New composables: `ConnectButton`, `ServicesDrawer`, `AppTabBar`, `AmneziaCard`, `AmneziaPrimaryButton`  
- `MainScreen` → scaffold with tabs; migrate current controls into Home/Settings/Logs  
- `AppViewModel` / prefs: `includeProxyInConnect`, `includeVpnInConnect` (default both true)  
- Keep existing start/stop / CF / logs ViewModel APIs  

## Success criteria

- Home reads as Amnezia-like: dark bg, apricot accent, circular Connect, bottom drawer + tabs  
- One Connect respects Include toggles; Stop stops all running  
- All current user actions reachable (proxy, VPN, ByeDPI settings, Telegram link, CF refresh, logs, battery, update)  
- No regression on foreground service / VPN permission flows  

## Spec self-review

- [x] No unresolved placeholders  
- [x] Stop vs Include semantics clarified  
- [x] Scope excludes ByeDPI prefs reskin  
- [x] Dual-service model matches approved B + approach 1  
- [x] No contradiction with dark-only decision  
