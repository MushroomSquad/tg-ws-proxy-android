# Client Router — product design (casual UX + hierarchical weights)

Date: 2026-07-31  
Status: approved  
Repo: [MushroomSquad/tg-ws-proxy-android](https://github.com/MushroomSquad/tg-ws-proxy-android)

## Goal

Replace the “two services + Include toggles” mental model with a **single client Router**.

The user only enables/disables **обход / ускорение интернета**. Strategy selection, routing, and weight updates on success/failure happen **on the client**. Protocol tweaks live in **Advanced**.

## Decisions (approved)

| Topic | Choice |
|-------|--------|
| Connect action | Starts/stops the **Router**, not a single protocol |
| Release scope | **One release**: four backends + weights + app/domain rules |
| Backends (groups) | TG WS · ByeDPI · AmneziaWG 2.0 · WireGuard |
| Configs for WG/AWG | Manual paste/import for now |
| Default group preference | TG WS (max) → ByeDPI (near) → AmneziaWG → WireGuard (fallback) |
| Hierarchy | Groups contain **strategies and/or VPN servers** (candidates), each with weights |
| Failover order | 1) next strategy in group → 2) next server in group → 3) next group |
| Simple Settings | Battery · How to use · Version · **Copy TG link** · **Open in Telegram** |
| Visual shell | Keep Amnezia-like dark Home (circular Connect); remove Include/Services as primary UX |

## Product model

### What the user does

1. Tap **CONNECT** → Router on.  
2. Tap **STOP** → Router off (all managed backends stopped).  
3. Optionally Copy link / Open Telegram from simple Settings.  
4. Everything else → Advanced.

### What the Router does

1. Pick enabled **StrategyGroup** with highest group weight.  
2. Inside it, pick **RouteCandidate** with highest candidate weight.  
3. Start the matching backend.  
4. On failure: demote candidate weight; try next candidate in the same group.  
5. If the group is exhausted for the current window: demote group weight; switch group.  
6. On success: promote candidate weight (and lightly the group), capped.

### Entities

```text
StrategyGroup
  id: tg_ws | byedpi | amneziawg | wireguard
  enabled: Boolean
  weight: Int
  candidates: List<RouteCandidate>

RouteCandidate
  id: String
  kind: strategy | vpn_server | local_proxy_profile
  groupId: StrategyGroupId
  weight: Int
  params: ByeDpiCmd | TgWsProfile | WireGuardConf | AmneziaWgConf | …
  lastSuccessAt / lastFailAt / failStreak

AppOrDomainRule
  match: packageName | domain
  forceGroup?: StrategyGroupId
  forceCandidateId?: String
```

## Home

```text
TgWsProxy

     ( CONNECT / STOP )
     Обход включён | Подключение… | Выкл
     (optional quiet: через TG WS — never a protocol picker)

[ Home | Settings | Advanced? or Settings→Advanced entry ]
```

- No Include Proxy / Include VPN on Home.  
- No primary “pick protocol” UI.  
- Status is casual; detailed route only in Advanced log if enabled.

## Simple Settings

- Battery optimization  
- How to use (first-run tip)  
- Version  
- **Copy TG link**  
- **Open in Telegram**  

Proxy port/secret editing can stay under Advanced (TG WS group), not on the casual Settings surface—unless already needed for Copy link (link uses current secret; editing secret is Advanced).

## Advanced

1. **Groups** — enable + group weight + reset weights; four cards.  
2. **Candidates inside group** — list with weights; add:
   - TG WS profile (usually one local proxy candidate)
   - ByeDPI strategy (cmd / preset string)
   - WireGuard `.conf` paste/import
   - AmneziaWG 2.0 config paste/import  
3. **Rules** — app package / domain → force group or force candidate.  
4. **Router log** — try / success / fail / switch (off by default).  
5. Deep links to existing ByeDPI preference screens where useful.

## Success / failure probes (client heuristics)

| Group | Success | Failure |
|-------|---------|---------|
| TG WS | Local proxy up + short probe toward DC/CF within timeout | Bind fail / probe timeout / N consecutive bridge errors |
| ByeDPI | VpnService up + tunnel/SOCKS alive + probe | VPN deny / native fail / probe fail |
| AmneziaWG | Tunnel up + handshake / peer alive | Handshake timeout / tunnel down |
| WireGuard | Same for WG | Same |

Exact probe targets and timeouts are implementation details; behavior must match the hierarchy above.

## Default weights (initial)

Suggested starting points (tunable in Advanced; exact numbers in implementation plan):

- Group: TG WS 100, ByeDPI 90, AmneziaWG 50, WireGuard 40  
- Within a group: new candidates start equal (e.g. 50); success/fail adjusts ±delta with min/max clamps  

## Out of scope

- Auto-deploy of remote Amnezia servers  
- Cloud strategy catalog sync  
- Light theme  
- Full reskin of ByeDPI Preference XML (deep-link OK)  
- Desktop clients  

## Relation to previous Amnezia UI work

Keep visual language (dark `#0E0E11`, accent `#FBB26A`, circular Connect).  
**Retire** as primary UX: Services Include toggles that expose dual engines.  
Router subsumes Connect/Stop semantics from `ConnectPlanner` / Include prefs.

## Success criteria

- New user can enable bypass with one tap without knowing TG WS vs DPI vs WG  
- Failover tries other strategies/servers **before** leaving the group  
- Manual WG/AWG configs appear as candidates and participate in weights  
- Simple Settings has Copy link + Open Telegram  
- Advanced can adjust weights and app/domain rules  

## Spec self-review

- [x] No unresolved placeholders for product decisions  
- [x] Hierarchy group → candidate → group switch documented  
- [x] Four backends named; manual configs acknowledged  
- [x] Simple Settings list includes TG actions per user correction  
- [x] One-release scope stated with honest native-stack risk  
- [x] Does not contradict casual Home / Advanced split  
