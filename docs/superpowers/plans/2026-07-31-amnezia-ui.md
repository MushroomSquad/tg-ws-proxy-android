# Amnezia-style UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reskin TgWsProxy Android to Amnezia dark UI — circular Connect, Services drawer with Include toggles, Home/Settings/Logs tabs — without changing proxy/VPN core services.

**Architecture:** Keep `ProxyForegroundService` / `ServiceManager` as-is. Add DataStore Include prefs + pure `ConnectPlanner` for start/stop decisions. Rebuild Compose UI: Amnezia theme tokens, `ConnectButton`, `ServicesSheet`, tab scaffold replacing the current scroll `MainScreen`.

**Tech Stack:** Kotlin, Jetpack Compose Material3, DataStore Preferences, existing ViewModel/Flow, JUnit for planner tests.

**Spec:** `docs/superpowers/specs/2026-07-31-amnezia-ui-design.md`

## Global Constraints

- Dark-only Amnezia palette (hex values from spec; no light theme)
- Include gates **Start** only; **Stop** stops all running managed services
- Do not reskin ByeDPI Preference XML; deep-link only
- Do not copy Amnezia PT Root UI font or proprietary SVGs — system sans + Material icons
- Do not change MTProto/proxy protocol or native ByeDPI JNI in this plan
- Corner radius 16dp; Connect ring ~190dp

## File map

| File | Responsibility |
|------|----------------|
| `app/.../ui/AmneziaColors.kt` | Color constants |
| `app/.../ui/Theme.kt` | Dark ColorScheme + Typography |
| `app/.../ui/ConnectPlanner.kt` | Pure start/stop + status string logic |
| `app/.../ui/ConnectButton.kt` | Circular Connect/Stop control |
| `app/.../ui/AmneziaComponents.kt` | Card, primary button, tab bar bits |
| `app/.../ui/ServicesSheet.kt` | Collapsed peek + expanded sheet content |
| `app/.../ui/HomeTab.kt` | Home layout wiring Connect + sheet |
| `app/.../ui/SettingsTab.kt` | Settings cards |
| `app/.../ui/LogsTab.kt` | Logs viewer |
| `app/.../ui/MainScreen.kt` | Tab scaffold + dialogs |
| `app/.../ui/AppViewModel.kt` | UiState Include fields + setters |
| `app/.../data/ConfigRepository.kt` | Persist Include prefs |
| `app/.../MainActivity.kt` | `onConnect` / `onDisconnect` orchestration |
| `app/src/test/.../ConnectPlannerTest.kt` | Unit tests for planner |
| `app/src/main/res/values/themes.xml` | Window background `#0E0E11` if needed |

---

### Task 1: Amnezia theme tokens

**Files:**
- Create: `app/src/main/java/com/flowseal/tgwsproxy/ui/AmneziaColors.kt`
- Modify: `app/src/main/java/com/flowseal/tgwsproxy/ui/Theme.kt`
- Modify: `app/src/main/res/values/themes.xml` (windowBackground)
- Modify: `app/src/main/res/values/colors.xml` if present

**Interfaces:**
- Produces: `object AmneziaColors` with `Bg`, `Surface`, `Border`, `Text`, `Muted`, `Accent`, `Error`, `ButtonFill`, `ButtonOn` as `Color`
- Produces: `TgWsTheme` always uses dark Amnezia scheme (ignore system light)

- [ ] **Step 1: Add `AmneziaColors.kt`**

```kotlin
package com.flowseal.tgwsproxy.ui

import androidx.compose.ui.graphics.Color

object AmneziaColors {
    val Bg = Color(0xFF0E0E11)
    val Surface = Color(0xFF1C1D21)
    val Border = Color(0xFF2C2D30)
    val Text = Color(0xFFD7D8DB)
    val Muted = Color(0xFF878B91)
    val Accent = Color(0xFFFBB26A)
    val Error = Color(0xFFEB5757)
    val ButtonFill = Color(0xFFD7D8DB)
    val ButtonOn = Color(0xFF0E0E11)
}
```

- [ ] **Step 2: Rewrite `Theme.kt` to dark-only Amnezia**

```kotlin
private val AmneziaDark = darkColorScheme(
    primary = AmneziaColors.Accent,
    onPrimary = AmneziaColors.ButtonOn,
    secondary = AmneziaColors.Muted,
    background = AmneziaColors.Bg,
    surface = AmneziaColors.Surface,
    onBackground = AmneziaColors.Text,
    onSurface = AmneziaColors.Text,
    error = AmneziaColors.Error,
    outline = AmneziaColors.Border,
)

@Composable
fun TgWsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AmneziaDark,
        typography = Typography(
            headlineLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, color = AmneziaColors.Text),
            headlineMedium = TextStyle(fontSize = 25.sp, fontWeight = FontWeight.Bold, color = AmneziaColors.Text),
            bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, color = AmneziaColors.Text),
            labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, color = AmneziaColors.Muted),
            labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AmneziaColors.Text),
        ),
        content = content,
    )
}
```

- [ ] **Step 3: Set Android window background to `#0E0E11` in `themes.xml`**

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/flowseal/tgwsproxy/ui/AmneziaColors.kt \
  app/src/main/java/com/flowseal/tgwsproxy/ui/Theme.kt \
  app/src/main/res/values/themes.xml app/src/main/res/values/colors.xml
git commit -m "Add Amnezia dark theme tokens for Compose UI."
```

---

### Task 2: ConnectPlanner + unit tests

**Files:**
- Create: `app/src/main/java/com/flowseal/tgwsproxy/ui/ConnectPlanner.kt`
- Create: `app/src/test/java/com/flowseal/tgwsproxy/ui/ConnectPlannerTest.kt`

**Interfaces:**
- Produces:
```kotlin
enum class ConnectVisual { Disconnected, Connecting, Connected }

data class ConnectSnapshot(
    val includeProxy: Boolean,
    val includeVpn: Boolean,
    val proxyRunning: Boolean,
    val vpnRunning: Boolean,
    val connecting: Boolean = false,
)

object ConnectPlanner {
    fun anyIncluded(s: ConnectSnapshot): Boolean
    fun anyRunning(s: ConnectSnapshot): Boolean
    fun visual(s: ConnectSnapshot): ConnectVisual
    fun statusLine(s: ConnectSnapshot): String
    /** Services to start on Connect press (included && !running). */
    fun servicesToStart(s: ConnectSnapshot): Set<ServiceKind>
    /** Stop always targets all running managed services. */
    fun servicesToStop(s: ConnectSnapshot): Set<ServiceKind>
}

enum class ServiceKind { Proxy, Vpn }
```

- [ ] **Step 1: Write failing tests**

```kotlin
class ConnectPlannerTest {
    @Test fun bothIncludeOff_connectDisabled() {
        val s = ConnectSnapshot(false, false, false, false)
        assertFalse(ConnectPlanner.anyIncluded(s))
        assertEquals(ConnectVisual.Disconnected, ConnectPlanner.visual(s))
        assertTrue(ConnectPlanner.servicesToStart(s).isEmpty())
    }

    @Test fun startOnlyIncludedNotRunning() {
        val s = ConnectSnapshot(includeProxy = true, includeVpn = true, proxyRunning = true, vpnRunning = false)
        assertEquals(setOf(ServiceKind.Vpn), ConnectPlanner.servicesToStart(s))
    }

    @Test fun stopStopsAllRunningRegardlessOfInclude() {
        val s = ConnectSnapshot(includeProxy = false, includeVpn = true, proxyRunning = true, vpnRunning = true)
        assertEquals(setOf(ServiceKind.Proxy, ServiceKind.Vpn), ConnectPlanner.servicesToStop(s))
    }

    @Test fun statusLineFormats() {
        assertEquals("All off", ConnectPlanner.statusLine(ConnectSnapshot(true, true, false, false)))
        assertEquals("Both on", ConnectPlanner.statusLine(ConnectSnapshot(true, true, true, true)))
        assertEquals("Proxy on · VPN off", ConnectPlanner.statusLine(ConnectSnapshot(true, true, true, false)))
    }

    @Test fun connectingVisual() {
        val s = ConnectSnapshot(true, true, false, false, connecting = true)
        assertEquals(ConnectVisual.Connecting, ConnectPlanner.visual(s))
    }
}
```

- [ ] **Step 2: Run tests — expect FAIL (class missing)**

```bash
cd /mnt/Soft/Work/Projects/tg-ws-proxy && ./gradlew :app:testDebugUnitTest --tests com.flowseal.tgwsproxy.ui.ConnectPlannerTest
```

- [ ] **Step 3: Implement `ConnectPlanner.kt` to match tests**

Status rules:
- `All off` if neither running
- `Both on` if both running
- `Proxy on · VPN off` / `Proxy off · VPN on` otherwise
- `visual`: Connecting if `connecting`; else Connected if `anyRunning`; else Disconnected

- [ ] **Step 4: Run tests — expect PASS**

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowseal/tgwsproxy/ui/ConnectPlanner.kt \
  app/src/test/java/com/flowseal/tgwsproxy/ui/ConnectPlannerTest.kt
git commit -m "Add ConnectPlanner with unit tests for Include/Start/Stop."
```

---

### Task 3: Persist Include prefs in ConfigRepository + UiState

**Files:**
- Modify: `app/src/main/java/com/flowseal/tgwsproxy/data/ConfigRepository.kt`
- Modify: `app/src/main/java/com/flowseal/tgwsproxy/ui/AppViewModel.kt`
- Test: extend planner usage only (manual); optional small test not required if DataStore hard to unit-test without Robolectric

**Interfaces:**
- Produces on `ConfigRepository`:
  - `val includeProxyFlow: Flow<Boolean>` default `true`
  - `val includeVpnFlow: Flow<Boolean>` default `true`
  - `suspend fun setIncludeProxy(value: Boolean)`
  - `suspend fun setIncludeVpn(value: Boolean)`
- Extends `UiState`:
```kotlin
data class UiState(
    // existing fields…
    val includeProxy: Boolean = true,
    val includeVpn: Boolean = true,
    val connecting: Boolean = false,
    val showLogs: Boolean = false, // UI-only remember OK; optional persist later
)
```
- Produces ViewModel:
  - `fun setIncludeProxy(v: Boolean)`
  - `fun setIncludeVpn(v: Boolean)`
  - `fun setConnecting(v: Boolean)`

- [ ] **Step 1: Add DataStore keys `include_proxy_connect` / `include_vpn_connect` (boolean, default true)**

- [ ] **Step 2: Expose flows + setters on repository**

- [ ] **Step 3: Combine into `UiState` in `AppViewModel.state`**

Use nested `combine` carefully (existing pattern with `Extra` / `ServiceFlags`). Add Include flags to combine inputs.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/flowseal/tgwsproxy/data/ConfigRepository.kt \
  app/src/main/java/com/flowseal/tgwsproxy/ui/AppViewModel.kt
git commit -m "Persist Include-in-Connect prefs for proxy and VPN."
```

---

### Task 4: ConnectButton + shared Amnezia components

**Files:**
- Create: `app/src/main/java/com/flowseal/tgwsproxy/ui/ConnectButton.kt`
- Create: `app/src/main/java/com/flowseal/tgwsproxy/ui/AmneziaComponents.kt`

**Interfaces:**
```kotlin
@Composable
fun ConnectButton(
    visual: ConnectVisual,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
)

@Composable
fun AmneziaCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit)

@Composable
fun AmneziaPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true)

@Composable
fun AmneziaTabBar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
)

enum class AppTab { Home, Settings, Logs }
```

- [ ] **Step 1: Implement `ConnectButton`** — 190dp circle, 3dp stroke; color `Text` when Disconnected, `Accent` when Connected; Connecting = rotating arc via `Canvas` + `InfiniteTransition` (skip animation if `LocalAccessibilityManager` / check `MotionDurationScale` or `remember { }` with `AccessibilityManager.isTouchExplorationEnabled` — simpler: use `androidx.compose.ui.platform.LocalContext` and `Settings.Global.ANIMATOR_DURATION_SCALE` optional; minimum: honor `LocalInspectionMode` and document reduce-motion as no-spin when duration scale is 0)

Label: `CONNECT` / `STOP` / `…` based on visual; font 20.sp Bold.

- [ ] **Step 2: Implement `AmneziaCard`** — background Surface, border Border 1dp, radius 16, padding 16

- [ ] **Step 3: Implement `AmneziaPrimaryButton`** — height 56, radius 16, fill ButtonFill, content ButtonOn

- [ ] **Step 4: Implement `AmneziaTabBar`** — Surface bg, top Border, three icons Home/Settings/Article; selected Accent, else Text

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowseal/tgwsproxy/ui/ConnectButton.kt \
  app/src/main/java/com/flowseal/tgwsproxy/ui/AmneziaComponents.kt
git commit -m "Add ConnectButton and shared Amnezia Compose components."
```

---

### Task 5: ServicesSheet + HomeTab

**Files:**
- Create: `app/src/main/java/com/flowseal/tgwsproxy/ui/ServicesSheet.kt`
- Create: `app/src/main/java/com/flowseal/tgwsproxy/ui/HomeTab.kt`

**Interfaces:**
```kotlin
@Composable
fun HomeTab(
    state: UiState,
    drawerExpanded: Boolean,
    onDrawerExpandedChange: (Boolean) -> Unit,
    onConnectClick: () -> Unit,
    onIncludeProxy: (Boolean) -> Unit,
    onIncludeVpn: (Boolean) -> Unit,
    onOpenTelegram: () -> Unit,
    onCopyLink: () -> Unit,
    onOpenByeDpiSettings: () -> Unit,
    onBatteryHint: () -> Unit,
    onRefreshCf: () -> Unit,
)

@Composable
fun ServicesSheetContent( /* same callbacks for expanded body */ )
```

- [ ] **Step 1: Build Home column** — centered `ConnectButton` using `ConnectPlanner.visual` / `anyIncluded`; status line under button from `ConnectPlanner.statusLine`

- [ ] **Step 2: Collapsed peek** — handle 20×2, “Services” headlineMedium, muted status; tap toggles expand

- [ ] **Step 3: Expanded content** — use `ModalBottomSheet` (Material3) **or** animated height panel above tab bar. Prefer `ModalBottomSheet` with `sheetState` for Amnezia-like expand; on dismiss set `drawerExpanded=false`.

Expanded sections per spec: Proxy Include+actions, VPN Include+settings, Quick actions, hint text.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/flowseal/tgwsproxy/ui/ServicesSheet.kt \
  app/src/main/java/com/flowseal/tgwsproxy/ui/HomeTab.kt
git commit -m "Add Home tab with Connect and Services sheet."
```

---

### Task 6: SettingsTab + LogsTab

**Files:**
- Create: `app/src/main/java/com/flowseal/tgwsproxy/ui/SettingsTab.kt`
- Create: `app/src/main/java/com/flowseal/tgwsproxy/ui/LogsTab.kt`

**Interfaces:**
```kotlin
@Composable
fun SettingsTab(
    state: UiState,
    onSave: (ProxyConfig) -> Unit,
    onRefreshCf: () -> Unit,
    onBatteryHint: () -> Unit,
    onShowFirstRun: () -> Unit,
    onOpenByeDpiSettings: () -> Unit,
    onOpenUpdate: (String) -> Unit,
)

@Composable
fun LogsTab(
    state: UiState,
    showLogs: Boolean,
    onShowLogsChange: (Boolean) -> Unit,
    onClear: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
)
```

- [ ] **Step 1: SettingsTab** — four `AmneziaCard`s (Proxy fields port/secret/dcIp + Save with apply-on-restart snackbar text if `proxyRunning`; Cloudflare blurb + Update; App battery/version/update; ByeDPI open). Use existing `ProxyConfig` fields from `state.config`.

- [ ] **Step 2: LogsTab** — Switch show logs; if on, monospace `state.logTail`; Clear / Export / Share as Outline/Text buttons in Amnezia colors; optional scroll-to-top FAB when scrolled.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/flowseal/tgwsproxy/ui/SettingsTab.kt \
  app/src/main/java/com/flowseal/tgwsproxy/ui/LogsTab.kt
git commit -m "Add Settings and Logs tabs in Amnezia style."
```

---

### Task 7: MainScreen scaffold + dialogs + BackHandler

**Files:**
- Modify: `app/src/main/java/com/flowseal/tgwsproxy/ui/MainScreen.kt` (replace body)

**Interfaces:**
```kotlin
@Composable
fun MainScreen(
    state: UiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onIncludeProxy: (Boolean) -> Unit,
    onIncludeVpn: (Boolean) -> Unit,
    onOpenByeDpiSettings: () -> Unit,
    onCopyLink: () -> Unit,
    onOpenTelegram: () -> Unit,
    onSaveSettings: (ProxyConfig) -> Unit,
    onClearLogs: () -> Unit,
    onExportLogs: () -> Unit = {},
    onShareLogs: () -> Unit = {},
    onDismissFirstRun: () -> Unit,
    onShowFirstRun: () -> Unit = {},
    onBatteryHint: () -> Unit,
    onDismissUpdate: () -> Unit = {},
    onOpenUpdate: (String) -> Unit = {},
    onRefreshComponents: () -> Unit = {},
)
```

Connect click logic in UI:
```kotlin
val snap = ConnectSnapshot(state.includeProxy, state.includeVpn, state.proxyRunning, state.vpnRunning, state.connecting)
if (ConnectPlanner.anyRunning(snap)) onDisconnect() else onConnect()
```

- [ ] **Step 1: Scaffold** — `Column` fillMaxSize bg Bg; content `when(tab)`; bottom `AmneziaTabBar`

- [ ] **Step 2: Restyle first-run + update AlertDialogs** to Amnezia colors (container Surface, text Text, confirm ButtonFill)

- [ ] **Step 3: `BackHandler`** — if drawer expanded → collapse; else if tab != Home → Home; else default

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/flowseal/tgwsproxy/ui/MainScreen.kt
git commit -m "Replace MainScreen with Amnezia tab scaffold."
```

---

### Task 8: MainActivity Connect/Disconnect orchestration

**Files:**
- Modify: `app/src/main/java/com/flowseal/tgwsproxy/MainActivity.kt`

**Interfaces:**
- Consumes: `ConnectPlanner.servicesToStart` / `servicesToStop`
- Wire `MainScreen` new callbacks

- [ ] **Step 1: Implement `onConnect`**

```kotlin
fun performConnect(vm: AppViewModel) {
    val s = vm.state.value
    val snap = ConnectSnapshot(s.includeProxy, s.includeVpn, s.proxyRunning, s.vpnRunning)
    val toStart = ConnectPlanner.servicesToStart(snap)
    vm.setConnecting(true)
    if (ServiceKind.Proxy in toStart) {
        ProxyForegroundService.start(this)
    }
    if (ServiceKind.Vpn in toStart) {
        requestVpnAndStart(vm) // existing launcher; on deny toast already
    } else {
        vm.setConnecting(false)
    }
    vm.refresh()
    // Clear connecting when both targets running or after VPN callback — setConnecting(false) in vpn launcher success/fail and after proxy start
}
```

On VPN deny: still leave proxy running if started; `Toast` / snackbar already; `setConnecting(false)`.

- [ ] **Step 2: Implement `onDisconnect`**

```kotlin
fun performDisconnect(vm: AppViewModel) {
    val s = vm.state.value
    val stop = ConnectPlanner.servicesToStop(
        ConnectSnapshot(s.includeProxy, s.includeVpn, s.proxyRunning, s.vpnRunning)
    )
    if (ServiceKind.Proxy in stop) ProxyForegroundService.stop(this)
    if (ServiceKind.Vpn in stop) ServiceManager.stopVpn(this)
    vm.setConnecting(false)
    vm.refresh()
}
```

- [ ] **Step 3: Update `MainScreen(...)` call site** — remove old onStartProxy/onStopProxy/onStartVpn/onStopVpn from UI; keep helpers for sheet if needed via connect only

- [ ] **Step 4: Smoke-compile**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/flowseal/tgwsproxy/MainActivity.kt \
  app/src/main/java/com/flowseal/tgwsproxy/ui/AppViewModel.kt \
  app/src/main/java/com/flowseal/tgwsproxy/ui/MainScreen.kt
git commit -m "Wire Connect/Disconnect to proxy and VPN via Include prefs."
```

---

### Task 9: Polish, version bump, verify

**Files:**
- Modify: `app/build.gradle.kts` — `versionName` → `1.2.0`, `versionCode` → `16`
- Modify: `docs/superpowers/specs/2026-07-31-amnezia-ui-design.md` — Status: approved/implemented
- Optional: `README.md` one-line UI note

- [ ] **Step 1: Bump version to 1.2.0 / 16**

- [ ] **Step 2: Run unit tests**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: PASS including `ConnectPlannerTest`

- [ ] **Step 3: Assemble debug APK**

```bash
./gradlew :app:assembleDebug
```

Expected: SUCCESS

- [ ] **Step 4: Manual checklist (device)**  
  - Connect with both Include on → both start  
  - Stop → both stop  
  - Include VPN off → Connect starts proxy only  
  - VPN deny → proxy still up if included  
  - Tabs Settings/Logs work; ByeDPI settings opens  
  - Open Telegram / Copy link from sheet  

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts docs/superpowers/specs/2026-07-31-amnezia-ui-design.md README.md
git commit -m "Bump to 1.2.0 after Amnezia-style UI refresh."
```

---

## Spec coverage self-review

| Spec item | Task |
|-----------|------|
| Amnezia colors / dark-only | 1 |
| Circular Connect + states | 4, 5 |
| Include Start / Stop all running | 2, 3, 8 |
| Services drawer | 5 |
| Tabs Home/Settings/Logs | 5–7 |
| Settings cards + Save | 6 |
| Logs toggle/export | 6 |
| First-run / update dialogs | 7 |
| Back handling | 7 |
| ByeDPI deep-link only | 5, 6 |
| Motion / reduce-motion note | 4 |
| No protocol/JNI changes | all |

## Placeholder scan

No TBD/TODO left in task steps. Types (`ConnectSnapshot`, `ServiceKind`, `AppTab`, `ConnectVisual`) defined in Tasks 2 and 4 and reused consistently.
