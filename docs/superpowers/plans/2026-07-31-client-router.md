# Client Router Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a casual one-tap Router that picks among TG WS, ByeDPI, AmneziaWG 2.0, and WireGuard via hierarchical weighted failover, with Advanced for weights/candidates/rules and simple Settings for TG link actions.

**Architecture:** Pure Kotlin `RouterEngine` + `WeightStore` decide the next `RouteCandidate`. `RouterController` starts/stops backends through a `Backend` interface (`TgWsBackend`, `ByeDpiBackend`, `WireGuardBackend`, `AmneziaWgBackend`). Probes report success/fail back into the engine. UI: Home = Router on/off; Settings = casual; Advanced = groups/candidates/rules.

**Tech Stack:** Kotlin, Jetpack Compose, DataStore/JSON persistence, existing `ProxyForegroundService` + `ByeDpiVpnService`, WireGuard Android tunnel (`com.wireguard.android:tunnel`), AmneziaWG via vendored `amnezia-vpn/amneziawg-android` tunnel APIs (AWG 2.0), JUnit for engine tests.

**Spec:** `docs/superpowers/specs/2026-07-31-client-router-design.md`

## Scope note

This is one release train with **four backends**. Tasks 1–5 deliver a working Router on TG WS + ByeDPI (current code). Tasks 6–7 add WireGuard and AmneziaWG. Do not ship a release claiming four groups until Tasks 6–7 pass device checks; until then keep disabled groups invisible or labeled “needs config”.

## Global Constraints

- Connect starts/stops the **Router**, not a single protocol
- Four groups: `tg_ws` | `byedpi` | `amneziawg` | `wireguard`
- Failover: next strategy/server **in group** before next **group**
- Default group weights: TG WS **100**, ByeDPI **90**, AmneziaWG **50**, WireGuard **40**
- New candidate weight default **50**; clamps **1..100**; success **+5** candidate / **+1** group; fail **-15** candidate; after group exhausted in window group **-10**
- Simple Settings: Battery · How to use · Version · Copy TG link · Open in Telegram
- Advanced holds weights, candidates, rules, config paste, router log
- Home stays Amnezia-dark; no Include toggles on Home
- Manual WG/AWG configs only (no server auto-deploy)
- Do not reskin ByeDPI Preference XML (deep-link OK)
- Package id remains `com.flowseal.tgwsproxy`

## File map

| Path | Responsibility |
|------|----------------|
| `app/.../router/model/RouterModels.kt` | `StrategyGroupId`, `RouteCandidate`, `StrategyGroup`, `AppOrDomainRule`, `CandidateParams` |
| `app/.../router/WeightPolicy.kt` | Pure weight update math |
| `app/.../router/RouterEngine.kt` | Pick next candidate; record success/fail; hierarchy |
| `app/.../router/RouterStateStore.kt` | Persist groups/candidates/rules/weights (DataStore JSON) |
| `app/.../router/probe/Probe.kt` | `RouteProbe` interface + results |
| `app/.../router/probe/TgWsProbe.kt` | TCP connect probe to configured DC/CF |
| `app/.../router/probe/ByeDpiProbe.kt` | Tunnel up + HTTP/TCP probe via local SOCKS |
| `app/.../router/probe/TunnelProbe.kt` | Shared WG/AWG handshake/peer check |
| `app/.../router/backend/Backend.kt` | `Backend` interface |
| `app/.../router/backend/TgWsBackend.kt` | Wrap `ProxyForegroundService` |
| `app/.../router/backend/ByeDpiBackend.kt` | Wrap `ServiceManager` + VPN prepare |
| `app/.../router/backend/WireGuardBackend.kt` | Official WG tunnel |
| `app/.../router/backend/AmneziaWgBackend.kt` | AWG 2.0 tunnel |
| `app/.../router/RouterController.kt` | Orchestrate engine + backends + probes |
| `app/.../ui/HomeTab.kt` | Casual status only |
| `app/.../ui/SettingsTab.kt` | Simple settings list |
| `app/.../ui/advanced/*` | Advanced screens |
| `app/.../ui/MainScreen.kt` | Tabs: Home / Settings / Advanced |
| `app/.../ui/AppViewModel.kt` | Expose router state; drop Include-as-primary |
| `app/src/test/.../router/*` | Engine + weight tests |

---

### Task 1: Domain model + WeightPolicy (TDD)

**Files:**
- Create: `app/src/main/java/com/flowseal/tgwsproxy/router/model/RouterModels.kt`
- Create: `app/src/main/java/com/flowseal/tgwsproxy/router/WeightPolicy.kt`
- Create: `app/src/test/java/com/flowseal/tgwsproxy/router/WeightPolicyTest.kt`

**Interfaces:**
```kotlin
enum class StrategyGroupId { TgWs, ByeDpi, AmneziaWg, WireGuard }

sealed class CandidateParams {
    data class TgWsProfile(val profileId: String = "default") : CandidateParams()
    data class ByeDpiStrategy(val cmdArgs: String) : CandidateParams()
    data class WireGuardConf(val confText: String) : CandidateParams()
    data class AmneziaWgConf(val confText: String) : CandidateParams()
}

data class RouteCandidate(
    val id: String,
    val groupId: StrategyGroupId,
    val displayName: String,
    val weight: Int = 50,
    val enabled: Boolean = true,
    val params: CandidateParams,
    val failStreak: Int = 0,
)

data class StrategyGroup(
    val id: StrategyGroupId,
    val enabled: Boolean = true,
    val weight: Int,
    val candidates: List<RouteCandidate>,
)

data class AppOrDomainRule(
    val id: String,
    val packageName: String? = null,
    val domain: String? = null,
    val forceGroupId: StrategyGroupId? = null,
    val forceCandidateId: String? = null,
)

object WeightPolicy {
    const val GROUP_TG_WS = 100
    const val GROUP_BYE_DPI = 90
    const val GROUP_AMNEZIA_WG = 50
    const val GROUP_WIREGUARD = 40
    const val CANDIDATE_DEFAULT = 50
    const val MIN = 1
    const val MAX = 100
    fun onSuccess(candidateWeight: Int, groupWeight: Int): Pair<Int, Int>
    fun onFail(candidateWeight: Int): Int
    fun onGroupExhausted(groupWeight: Int): Int
    fun clamp(v: Int): Int = v.coerceIn(MIN, MAX)
}
```

- [ ] **Step 1: Write failing tests**

```kotlin
class WeightPolicyTest {
    @Test fun successPromotesCandidateAndGroup() {
        val (c, g) = WeightPolicy.onSuccess(50, 90)
        assertEquals(55, c)
        assertEquals(91, g)
    }
    @Test fun failDemotesCandidate() {
        assertEquals(35, WeightPolicy.onFail(50))
    }
    @Test fun clampAtBounds() {
        assertEquals(100, WeightPolicy.onSuccess(98, 100).first) // 98+5 -> 100
        assertEquals(1, WeightPolicy.onFail(10)) // 10-15 -> 1
    }
    @Test fun groupExhausted() {
        assertEquals(80, WeightPolicy.onGroupExhausted(90))
    }
}
```

- [ ] **Step 2: Run tests — expect FAIL**

```bash
./gradlew :app:testDebugUnitTest --tests com.flowseal.tgwsproxy.router.WeightPolicyTest
```

- [ ] **Step 3: Implement `WeightPolicy` + models exactly as interfaces**

- [ ] **Step 4: Run tests — expect PASS**

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowseal/tgwsproxy/router/model/RouterModels.kt \
  app/src/main/java/com/flowseal/tgwsproxy/router/WeightPolicy.kt \
  app/src/test/java/com/flowseal/tgwsproxy/router/WeightPolicyTest.kt
git commit -m "Add router domain model and weight policy with tests."
```

---

### Task 2: RouterEngine hierarchical selection (TDD)

**Files:**
- Create: `app/src/main/java/com/flowseal/tgwsproxy/router/RouterEngine.kt`
- Create: `app/src/test/java/com/flowseal/tgwsproxy/router/RouterEngineTest.kt`

**Interfaces:**
```kotlin
data class RouterSnapshot(
    val groups: List<StrategyGroup>,
    val rules: List<AppOrDomainRule> = emptyList(),
    /** Candidate ids already failed in the current failover window for their group. */
    val exhaustedCandidateIds: Set<String> = emptySet(),
    val exhaustedGroupIds: Set<StrategyGroupId> = emptySet(),
)

data class Selection(
    val group: StrategyGroup,
    val candidate: RouteCandidate,
)

class RouterEngine {
    fun select(snapshot: RouterSnapshot, forPackage: String? = null, forDomain: String? = null): Selection?
    fun afterSuccess(snapshot: RouterSnapshot, candidateId: String): RouterSnapshot
    fun afterFail(snapshot: RouterSnapshot, candidateId: String): Pair<RouterSnapshot, Selection?>
    fun resetWindow(snapshot: RouterSnapshot): RouterSnapshot
}

fun defaultBootstrapGroups(): List<StrategyGroup> // TG WS default profile + ByeDPI default strategy; AWG/WG empty candidates, enabled=true
```

**Selection rules (must match tests):**
1. If a rule matches package/domain with `forceCandidateId` and candidate enabled → that candidate.  
2. Else if rule `forceGroupId` → pick highest-weight enabled non-exhausted candidate in that group.  
3. Else pick highest-weight enabled group not in `exhaustedGroupIds` with at least one usable candidate.  
4. Inside group: highest-weight enabled candidate not in `exhaustedCandidateIds`.  
5. `afterFail`: apply `WeightPolicy.onFail`, add candidate to exhausted set; if no usable candidates left in group, apply `onGroupExhausted`, exhaust group, select next; else select next in group.  
6. `afterSuccess`: apply `onSuccess`, clear window exhausted sets.

- [ ] **Step 1: Write failing tests** covering: prefers TG WS over ByeDPI by default weights; fail strategy stays in ByeDPI until candidates exhausted then switches group; rule forces WireGuard candidate; empty AWG/WG groups skipped.

- [ ] **Step 2: Run — expect FAIL**

```bash
./gradlew :app:testDebugUnitTest --tests com.flowseal.tgwsproxy.router.RouterEngineTest
```

- [ ] **Step 3: Implement `RouterEngine` + `defaultBootstrapGroups()`**

Default ByeDPI candidate cmd: empty string meaning “use UI prefs” (`CandidateParams.ByeDpiStrategy("")` treated by backend as current SharedPreferences UI mode). Default TG WS: `TgWsProfile("default")`.

- [ ] **Step 4: Tests PASS**

- [ ] **Step 5: Commit**

```bash
git commit -am "Add RouterEngine hierarchical failover with unit tests."
```

---

### Task 3: RouterStateStore persistence

**Files:**
- Create: `app/src/main/java/com/flowseal/tgwsproxy/router/RouterStateStore.kt`
- Modify: `app/src/main/java/com/flowseal/tgwsproxy/data/ConfigRepository.kt` (optional: stop using Include as source of truth; keep keys for migration one release)

**Interfaces:**
```kotlin
class RouterStateStore(context: Context) {
    val snapshotFlow: Flow<RouterSnapshot>
    suspend fun getSnapshot(): RouterSnapshot
    suspend fun saveSnapshot(snapshot: RouterSnapshot)
    suspend fun upsertCandidate(candidate: RouteCandidate)
    suspend fun removeCandidate(id: String)
    suspend fun setGroup(group: StrategyGroup)
    suspend fun upsertRule(rule: AppOrDomainRule)
    suspend fun removeRule(id: String)
    suspend fun ensureBootstrapped() // write defaults if empty
}
```

Persist as a single JSON string in DataStore key `router_snapshot_json` using `org.json` or kotlinx.serialization if already on classpath; prefer `org.json` to avoid new deps unless serialization already present.

- [ ] **Step 1: Implement store + `ensureBootstrapped()`**

- [ ] **Step 2: Unit-test JSON round-trip of a snapshot with 2 candidates** in `RouterStateStoreTest` using Robolectric **only if** already configured; else extract pure `RouterSnapshotCodec` encode/decode and unit-test that without Android.

Prefer:

```kotlin
object RouterSnapshotCodec {
    fun encode(s: RouterSnapshot): String
    fun decode(raw: String): RouterSnapshot
}
```

Test codec in JVM unit tests.

- [ ] **Step 3: Commit**

```bash
git commit -am "Persist router snapshot (groups, candidates, rules, weights)."
```

---

### Task 4: Backend interface + TgWs/ByeDpi backends + probes

**Files:**
- Create: `app/src/main/java/com/flowseal/tgwsproxy/router/backend/Backend.kt`
- Create: `app/src/main/java/com/flowseal/tgwsproxy/router/backend/TgWsBackend.kt`
- Create: `app/src/main/java/com/flowseal/tgwsproxy/router/backend/ByeDpiBackend.kt`
- Create: `app/src/main/java/com/flowseal/tgwsproxy/router/probe/RouteProbe.kt`
- Create: `app/src/main/java/com/flowseal/tgwsproxy/router/probe/TgWsProbe.kt`
- Create: `app/src/main/java/com/flowseal/tgwsproxy/router/probe/ByeDpiProbe.kt`

**Interfaces:**
```kotlin
interface Backend {
    val groupId: StrategyGroupId
    suspend fun start(candidate: RouteCandidate): Result<Unit>
    suspend fun stop()
    fun isRunning(): Boolean
}

sealed class ProbeResult {
    data object Success : ProbeResult()
    data class Failure(val reason: String) : ProbeResult()
}

interface RouteProbe {
    suspend fun probe(candidate: RouteCandidate, timeoutMs: Long = 5_000): ProbeResult
}
```

`TgWsBackend.start`: `ProxyForegroundService.start(context)`.  
`TgWsBackend.stop`: `ProxyForegroundService.stop(context)`.  

`ByeDpiBackend.start`: if `ByeDpiStrategy.cmdArgs` non-blank, write to SharedPreferences `byedpi_enable_cmd_settings=true` + `byedpi_cmd_args`; then require VPN permission callback from Activity (see Task 5) and `ServiceManager.startVpn`.  
`ByeDpiBackend.stop`: `ServiceManager.stopVpn`.

`TgWsProbe`: open TCP to `127.0.0.1:port` from `ConfigRepository` within timeout (proxy listening = success for MVP); optional second step: connect to first DC IP from config with 2s timeout — if fails, still Success if local accept works (log soft-warn).  

`ByeDpiProbe`: if `byeDpiRunning` false → Failure; else TCP connect to ByeDPI SOCKS port from prefs (default 1080) → Success/Failure.

- [ ] **Step 1: Implement interfaces + backends + probes**

- [ ] **Step 2: Compile**

```bash
./gradlew :app:compileDebugKotlin
```

- [ ] **Step 3: Commit**

```bash
git commit -am "Add TG WS and ByeDPI router backends with probes."
```

---

### Task 5: RouterController + ViewModel + MainActivity wiring

**Files:**
- Create: `app/src/main/java/com/flowseal/tgwsproxy/router/RouterController.kt`
- Modify: `app/src/main/java/com/flowseal/tgwsproxy/ui/AppViewModel.kt`
- Modify: `app/src/main/java/com/flowseal/tgwsproxy/MainActivity.kt`
- Modify: `app/src/main/java/com/flowseal/tgwsproxy/ui/ConnectPlanner.kt` — leave file but stop using Include semantics from UI (or delete usages)

**Interfaces:**
```kotlin
enum class RouterUiPhase { Off, Connecting, On }

data class RouterUiState(
    val phase: RouterUiPhase = RouterUiPhase.Off,
    val activeGroupId: StrategyGroupId? = null,
    val activeCandidateId: String? = null,
    val quietDetail: String? = null, // e.g. "через TG WS"
)

class RouterController(
    private val store: RouterStateStore,
    private val engine: RouterEngine,
    private val backends: Map<StrategyGroupId, Backend>,
    private val probes: Map<StrategyGroupId, RouteProbe>,
) {
    val uiState: StateFlow<RouterUiState>
    suspend fun startRouter(vpnPrepare: suspend () -> Boolean): Result<Unit>
    suspend fun stopRouter()
}
```

`startRouter` loop:
1. `phase=Connecting`
2. `select` → start backend → probe  
3. success → `afterSuccess`, `phase=On`, break  
4. fail → stop backend, `afterFail` → if next selection null, `phase=Off` and return failure; else continue  
Max attempts: `candidates.size` across enabled groups capped at 12.

`stopRouter`: stop all backends that `isRunning()`, `phase=Off`, `resetWindow` persisted.

ViewModel: expose `routerUi` + `startRouter`/`stopRouter`; remove Home dependency on `includeProxy`/`includeVpn`.

MainActivity: Connect → `vm.startRouter { prepareVpn() }`; Stop → `vm.stopRouter()`.

- [ ] **Step 1: Implement controller + wire Activity**

- [ ] **Step 2: Manual smoke on device/emulator: Connect brings up TG WS (or ByeDPI if TG WS forced fail)**

- [ ] **Step 3: Commit**

```bash
git commit -am "Wire RouterController to Connect/Stop and ViewModel."
```

---

### Task 6: Casual Home + simple Settings + Advanced shell

**Files:**
- Modify: `app/src/main/java/com/flowseal/tgwsproxy/ui/HomeTab.kt` — remove Services Include UI; status from `RouterUiState` (`Обход включён` / `Подключение…` / `Выкл`); optional quiet detail  
- Modify: `app/src/main/java/com/flowseal/tgwsproxy/ui/SettingsTab.kt` — only Battery, How to use, Version, Copy TG link, Open in Telegram  
- Create: `app/src/main/java/com/flowseal/tgwsproxy/ui/advanced/AdvancedTab.kt` (placeholder list: Groups, Rules, Router log toggles — filled in Task 7–8)  
- Modify: `app/src/main/java/com/flowseal/tgwsproxy/ui/MainScreen.kt` — tabs Home / Settings / Advanced (Logs can move under Advanced or remain 4th tab; **keep Logs as 4th tab** to avoid losing debug)  
- Modify: `app/src/main/java/com/flowseal/tgwsproxy/ui/AmneziaComponents.kt` — `AppTab` add `Advanced`

Home Connect click: if `phase==On || Connecting` → stop else start.

Settings Copy/Open: reuse existing ViewModel `copyLink` / Activity open Telegram.

- [ ] **Step 1: Implement UI changes**

- [ ] **Step 2: Compile**

```bash
./gradlew :app:compileDebugKotlin
```

- [ ] **Step 3: Commit**

```bash
git commit -am "Simplify Home/Settings for Router; add Advanced tab shell."
```

---

### Task 7: Advanced — groups, candidates, rules UI + store edits

**Files:**
- Create: `app/src/main/java/com/flowseal/tgwsproxy/ui/advanced/GroupsScreen.kt`
- Create: `app/src/main/java/com/flowseal/tgwsproxy/ui/advanced/CandidateEditor.kt`
- Create: `app/src/main/java/com/flowseal/tgwsproxy/ui/advanced/RulesScreen.kt`
- Modify: `AdvancedTab.kt` to navigate among these (simple in-tab state machine or Compose `NavHost` already on classpath)

**Behavior:**
- Group card: enable switch, weight slider 1–100, open candidates list  
- Add ByeDPI strategy: multiline cmd → `ByeDpiStrategy`  
- Add WG/AWG: multiline conf → respective params (backends may still be stubs until Tasks 8–9 — show candidate anyway)  
- Rules: package name and/or domain fields → force group dropdown + optional candidate id  
- “Reset weights” restores defaults from `WeightPolicy` constants + candidate weights to 50  
- Router log switch stored in DataStore `router_log_enabled`

- [ ] **Step 1: Implement Advanced screens bound to `RouterStateStore`**

- [ ] **Step 2: Commit**

```bash
git commit -am "Add Advanced UI for groups, candidates, and rules."
```

---

### Task 8: WireGuard backend

**Files:**
- Modify: `app/build.gradle.kts` — add dependency  
```kotlin
implementation("com.wireguard.android:tunnel:1.0.20230706")
```
(If resolution fails, pin the latest `com.wireguard.android:tunnel` from Maven Central available at implement time.)
- Create: `app/src/main/java/com/flowseal/tgwsproxy/router/backend/WireGuardBackend.kt`
- Create: `app/src/main/java/com/flowseal/tgwsproxy/router/probe/TunnelProbe.kt`
- Modify: AndroidManifest as required by WG tunnel (VPN service if separate)
- Register `WireGuardBackend` in `RouterController` backends map

**Behavior:**
- Parse `CandidateParams.WireGuardConf.confText` via WG `Config.parse`  
- Bring up tunnel using tunnel library GoBackend / WgQuickBackend pattern from WireGuard Android samples  
- Only one tunnel backend active: starting WG must `stop()` ByeDPI/AWG/TGWS per controller (controller already stops previous on switch)  
- `TunnelProbe`: success if backend reports `UP` within timeout; else Failure  

- [ ] **Step 1: Add dependency and implement backend + probe**

- [ ] **Step 2: Device test with a pasted WG conf candidate forced via rule/weight**

- [ ] **Step 3: Commit**

```bash
git commit -am "Add WireGuard tunnel backend for router candidates."
```

---

### Task 9: AmneziaWG 2.0 backend

**Files:**
- Vendor or depend on AmneziaWG Android tunnel supporting AWG 2.0. Prefer integrating source from [amnezia-vpn/amneziawg-android](https://github.com/amnezia-vpn/amneziawg-android) tunnel modules as a Gradle included build **or** AAR if a maintained AWG2 artifact is available at implement time. Record the chosen artifact/commit in `NOTICE.md`.  
- Create: `app/src/main/java/com/flowseal/tgwsproxy/router/backend/AmneziaWgBackend.kt`
- Reuse `TunnelProbe` with AWG status API  
- Update `NOTICE.md` / `CREDITS.md` for AmneziaWG

**Behavior:**
- Parse AmneziaWG conf (including Jc/Jmin/Jmax/H1–H4/I1–I5 when present)  
- Start AWG tunnel; stop other backends via controller  
- Candidate params: `AmneziaWgConf`

- [ ] **Step 1: Integrate library/source and implement backend**

- [ ] **Step 2: Device test with pasted AWG2 conf**

- [ ] **Step 3: Commit**

```bash
git commit -am "Add AmneziaWG 2.0 tunnel backend for router candidates."
```

---

### Task 10: App/domain rules enforcement + polish

**Files:**
- Modify: `RouterController` to pass foreground app package when available (UsageStats optional — if permission missing, rules only apply to domain probes / manual force). **MVP:** apply `forceGroup`/`forceCandidate` only when `forPackage`/`forDomain` provided by caller; Settings Advanced “Test rule” button; automatic package detection is best-effort via `UsageStatsManager` if `PACKAGE_USAGE_STATS` granted, else skip.  
- Modify: Home quiet detail string from active group  
- Bump `versionName` to `1.3.0`, `versionCode` to `18`  
- Update `docs/superpowers/specs/2026-07-31-client-router-design.md` status to implemented  
- Update README short “Router” blurb  

- [ ] **Step 1: Implement best-effort rule context + version bump + docs**

- [ ] **Step 2: Run full unit tests + assembleDebug**

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

- [ ] **Step 3: Commit**

```bash
git commit -am "Enforce router rules best-effort; release 1.3.0."
```

---

## Spec coverage self-review

| Spec requirement | Task |
|------------------|------|
| One-tap Router | 5, 6 |
| Hierarchical failover | 2 |
| Weights defaults + updates | 1, 2 |
| Four groups | 2 bootstrap; 8–9 backends |
| Manual WG/AWG configs | 7 UI, 8–9 backends |
| Simple Settings TG actions | 6 |
| Advanced groups/candidates/rules | 7 |
| App/domain rules | 2 select + 7 UI + 10 |
| Casual Home | 6 |
| ByeDPI XML not reskinned | 4 deep-link only |

## Placeholder scan

No TBD. Library versions for WG/AWG may be adjusted at implement time to the newest resolving artifact, but the Backend contracts stay fixed.
