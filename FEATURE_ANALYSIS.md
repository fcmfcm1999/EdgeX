# Feature Analysis & Design Notes

## XPE (Xposed Edge Pro) Config Loading Architecture

### Cross-Process Config Mechanism

XPE does **not** use SharedPreferences or ContentProvider for cross-process config access. It uses two separate mechanisms:

**Initial Load — Direct File I/O (DE Storage)**
- Config is stored at `/data/data/com.jozein.xedgepro/files/prefs` in a custom binary format.
- Reading happens in `HookMain.initZygote()` (Zygote init phase), before system_server even starts.
- Because `files/` is **Device Encrypted (DE)** storage, it is accessible immediately after boot without requiring the user to unlock. This sidesteps the FBE problem entirely.
- Relevant classes: `a.p` (config model, `L()` loads from file), `f.p` (file I/O), `f.l` (path constants).

**Runtime Updates — Intent Broadcasts**
- When the UI changes a setting, it serializes the changed key-value directly into Intent extras and broadcasts it to system_server.
- The hook process has a `BroadcastReceiver` (rooted in `a.r`) that parses the Intent and updates the in-memory config immediately.
- No round-trip to a ContentProvider is needed.

### Why This Avoids the FBE Boot Problem

Android FBE has two storage classes:
- **DE (Device Encrypted)**: accessible right after boot, before user unlock. App's `files/` and `cache/` directories fall here by default.
- **CE (Credential Encrypted)**: only accessible after the user has entered their PIN/pattern/password. SharedPreferences (`shared_prefs/`) lives here.

XPE stores config in DE (`files/`), so it can read config during Zygote init — long before any user interaction. Our implementation uses SharedPreferences (CE storage), which is why the hook process gets empty config on boot until `ACTION_USER_UNLOCKED` fires.

### Our Fix

We listen for `Intent.ACTION_USER_UNLOCKED` in `registerScreenStateReceiver()`. When it fires, we force a config reload (`lastConfigLoad = 0` + `reloadConfigAsync()`). This is the minimal correct fix without rewriting the config system.

A deeper fix (following XPE's approach) would be to migrate config storage to `filesDir` (DE) and switch updates to Intent broadcasts instead of ContentObserver. That would eliminate the dependency on user unlock entirely, but it's a large refactor.

---

## Config Propagation (Current EdgeX Design)

```
UI Process (com.fan.edgex)
  └─ putConfig(key, value)
       ├─ SharedPreferences.edit().putString().apply()   [CE storage]
       └─ contentResolver.notifyChange(CONTENT_URI)
            └─ ContentObserver in hooked process
                 └─ reloadConfigAsync() → ContentProvider query → update configCache
```

**Known limitation**: On fresh boot, `configCache` is empty until either:
1. `ACTION_USER_UNLOCKED` fires and triggers a reload (our fix), or
2. The user manually changes a setting (triggers `notifyChange`).
