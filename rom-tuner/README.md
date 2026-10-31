# ROM Tuner

ROM Tuner is a settings app for the TB-X606V ROMs (Android 14 and 16). It groups the performance, memory, display and behaviour options of this tablet in one place and applies them through a small root daemon. It builds without Gradle.

<img src="docs/icon-light.png" width="96"> <img src="docs/icon-dark.png" width="96">

The launcher icon is a flat performance dial with a light and a dark variant. The dark variant (`res/drawable-night`) is used when the system is in dark mode, and a monochrome layer supports themed icons. The sources are `assets/icon.svg` and `assets/icon-dark.svg`.

## Features

The app opens from the launcher and from the top of the Settings homepage. A "Performance" quick settings tile cycles through the battery, balanced and performance profiles.

| Section | Options |
|---|---|
| Presets | Battery saver, Balanced, Smooth, Performance, Extreme |
| Performance | Power mode (battery, balanced, performance), GPU clock, maximum clock for the fast and the efficiency cores |
| Tuning | Overclock, CPU governor, thermal limits, I/O scheduler, read-ahead, swappiness, TCP congestion control, foreground and top-app boost, big-task rotation, cache pressure, write-back threshold, free memory reserve, swap read-ahead, memory watermark |
| Touch and clocks | Touch boost length, clock step and strength, clock drop and rise delay and clock headroom of the schedplus governor, deep idle (MCDI), scheduler statistics |
| Wi-Fi | Country code (Automatic, CA, US, GB, DE, FR, IN, AU, JP, MX, BR) |
| Appearance | Dark mode, pure black, accent colour, palette style, font, icon shape and icon pack (the last three only when the ROM ships those overlays) |
| Display and system UI | Navigation mode, animation speed, display size, boot animation |
| Picture quality | MediaTek MiraVision: video dynamic range, display dynamic range, ultra resolution, HDR video, sharpness index, colour profile index. The PQ HAL reads its `persist.vendor.sys.pq.*` properties only at start, so the daemon sets them and restarts `pq-2-2` when one changes. The sharpness and colour values are MediaTek indexes whose scale is not documented: 2 and 0 are the factory values, try the others by eye |
| Behaviour | Screen timeout, font size, stay awake while charging, auto-rotate, show taps, window blur, private DNS, background apps kept, cached app limit, Treble audio effects, ADB over network, serial console shell (off by default; it triggers Android's "Serial console enabled, performance is impacted" warning), Bluetooth MediaTek workaround |
| Actions | Restart System UI, reboot, compile apps now, per app compile (System, App speed), shortcuts to LineageOS settings screens and Dolby Atmos |

A fresh install starts on the Smooth preset.

## Presets

The presets were measured on the tablet with a scripted fling through the Settings app (janky frames from `dumpsys gfxinfo`) and an eight-thread compute loop.

| Preset | Janky frames | 95th percentile frame time |
|---|---|---|
| Battery saver | 7.4% | 34 ms |
| Balanced | 2.8% | 19 ms |
| Performance profile alone | 2.6% | 23 ms |
| Smooth (foreground boost 60, top-app boost 30, larger free-memory reserve) | 1.7% | 18 ms |
| Performance (boosts 100 and 70, larger write buffer) | 1.1% | 22 ms |

Extreme adds pinned top clocks, the performance governor and no thermal limit. It gave no smoother UI than Performance in these tests and runs hotter. Big-task rotation made the compute loop 20 to 30 percent slower, so no preset turns it on.

## How it works

The app never writes to the system itself. It appends `key=value` lines to `files/request` in its own data directory. The daemon in `device/tablet_tunerd.sh` watches that directory with `inotifyd`, checks each value against a strict character set, applies it, and keeps the master state in `/data/local/tmp/tuner.state`. A copy of the state and the lists of available governors, GPU clocks, schedulers and fonts go back to the app. The log is `/data/local/tmp/tuner.log`.

The MediaTek power HAL rewrites the power mode, the boosts and the minimum clock limits on touch boosts and when the screen changes state. The daemon compares those values every five seconds and writes its own back when they differ. When it sets a profile it writes `cpufreq_power_mode` before the clock limits, because the sports mode resets them.

The daemon also keeps the Treble Settings audio effects switched off. Those effects attach to every playback session and cut app audio after a few milliseconds on this tablet. The "Treble audio effects" switch lets you leave them alone.

Compile apps (System page, "App speed"): the request keys `compile_now` and `compile_all` are one-shot actions, not stored state. The daemon starts `/system/bin/dexopt_all.sh` in the background with `DEXOPT_FORCE=""` (skips apps that are already compiled) or `-f` (recompiles all), writes its output to `/data/local/tmp/dexopt.log` and its pid to `/data/local/tmp/dexopt.pid`, and ignores a second request while one run is alive. The script is not part of this repository or the image builder: it comes from the Android 17 tools and must be installed on the tablet. The daemon publishes `compile_status=idle|running|done|missing` in the state copy (`done` when the log ends with `DONE`, `missing` when the script is not installed) and republishes within five seconds when it changes. A run takes 10 to 15 minutes. After the first apps are compiled the non-forced run restarts SystemUI and the launcher.

Per app compile (System page, "App speed", "Per app compile"): the screen lists every enabled installed app, user and system, with its compile state, a search box and the filter All, Not compiled, Compiled. The app list comes from `PackageManager` (the manifest asks for `QUERY_ALL_PACKAGES`). The compile states cannot be read by the app, so the daemon writes them to `files/apps_state`: one `package=filter` line per package and a last line `apps_state_time=<epoch>`. The app treats a file without that last line as broken (partly written), keeps the list it had, and shows "Loading compile states" while it sends `refresh_apps=1`. Sorting is not compiled first, then compiled, then apps without code, each by label. "Compiled" means a native code filter: speed, speed-profile, everything, space and their `-profile` forms. Verify, quicken, run-from-apk, error, unknown and a package missing from the dump count as not compiled. "Compile all not compiled" sends `compile_now`, which only skips apps that are already at speed or better, so apps at speed-profile are compiled again.

Tapping an app opens a dialog with Compile (speed), Compile (speed profile), Reset and Cancel. The request keys are one-shot like `compile_now`:

| Key | Value | Does |
|---|---|---|
| `compile_pkg` | package name | `cmd package compile -m speed -f <pkg>`, then the stale copy cleanup and `am force-stop <pkg>` |
| `compile_pkg_profile` | package name | the same with `-m speed-profile` |
| `reset_pkg` | package name | `cmd package compile --reset <pkg>` (no cleanup or force-stop) |
| `refresh_apps` | `1` | rebuilds `apps_state` in the background (several seconds) |

The package name must match `^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$` (checked in `pkgok`, on top of the general character set check) and `pm path <pkg>` must succeed, both when the request arrives and again when the job starts. Jobs go into `/data/local/tmp/tuner.queue` and one background worker runs them one at a time; the directory `/data/local/tmp/tuner.q.lock` marks a live worker. A request while `compile_now` or `compile_all` runs is refused (status `failed`), and `compile_now` or `compile_all` is refused while the worker runs. The state copy gets these keys:

| Key | Values |
|---|---|
| `compile_pkg_status` | `<package>:running`, `<package>:done` or `<package>:failed` for the latest job only |
| `compile_queue` | comma separated packages that are waiting (the running one is not in it) |
| `apps_state_time` | epoch seconds of the last time `apps_state` was written |
| `apps_refresh` | `running`, `failed` (neither dump command gave output) or `idle` |

The cleanup after a compile is the `fixcache` step of `dexopt_all.sh` for one package: when an odex sits next to a system apk, `/data/dalvik-cache/arm64/<apk path with / as @>@classes.*` is deleted so the next start uses the odex. The tuner app itself is not force-stopped, because that would close its own screen. After each job the daemon re-reads that one package and replaces its line, and it rebuilds the whole list when a `compile_now` or `compile_all` run ends and when the app asks.

The list is parsed from `cmd package art dump` (`dumpsys package dexopt` if that prints nothing): a `[com.x]` header line, then `path:` blocks with `arm64: [status=speed-profile] [reason=bg-dexopt] [primary-abi]`. The status on the first such line after a header is taken (the base apk). The header may be indented. `sh tests/test_apps.sh` runs the parser, the name check, the queue worker and the state file against stubs of `cmd`, `pm` and `am`, with no device.

Touch and clocks, idle, memory watermark (Performance and Memory pages) are stored with the preset and in saved profiles. Each has a fixed list of allowed values, checked in `valid` when a request arrives and again by `sane` before a write, so a bad value in the state file falls back to its default. Writes go through `wn`, which skips a node that does not exist.

| Key | Allowed values | Default | Stock | Written to |
|---|---|---|---|---|
| `touch_duration_ms` | 100, 200, 400, 800 | 400 | 100 | `/proc/perfmgr/tchbst/user/usrtch`, line `duration <ns>` |
| `touch_opp` | 0, 1, 2 (0 is the top clock) | 0 | 2 | same file, line `touch_opp` |
| `touch_boost` | 0 to 100 | 100 | 80 | same file, line `init` |
| `schedstats` | 0, 1 | 0 | 0 | `/proc/sys/kernel/sched_schedstats` |
| `down_throttle` | 4, 10, 20, 40 (ms) | 20 | 4 | `/sys/devices/system/cpu/cpufreq/policy{0,4}/sched/down_throttle_nsec` |
| `up_throttle` | 0, 2, 5 (ms) | 0 | 0 | same directory, `up_throttle_nsec` |
| `cap_margin` | 1024, 1280, 1536, 1792 | 1536 | 1280 (1.25x) | `/sys/devices/system/cpu/eas/cap_margin_dvfs` |
| `mcdi` | 1 (on), 0 (off) | 1 | 1 | `cpu*/cpuidle/state1/disable`, written as 1 minus the value |
| `watermark` | 10, 20, 30 | 10 | 10 | `/proc/sys/vm/watermark_scale_factor` |

The two throttle files only exist while the governor is schedplus, and the governor forgets them when it is set again, so `tune` writes them after the governor and `enforce` writes them again whenever it has to put the governor back. Battery and Balanced use the stock column, Smooth, Performance and Extreme use the default column. `min_free_kbytes` is left alone.

Two settings are not in presets or profiles because they are about the place and the Android framework, not the speed:

- `wifi_country`: Automatic or one of CA, US, GB, DE, FR, IN, AU, JP, MX, BR, default CA. The daemon runs `cmd wifi force-country-code enabled XX` (`disabled` for Automatic). The override is only kept in memory, so the daemon sets it again whenever it sees a new `system_server` pid (the first run after boot included), retrying every 5 seconds for a minute while the Wi-Fi service starts, and when the value changes.
- `cached_apps`: Default, 16, 24, 32 or 48, written to `settings put global activity_manager_constants max_cached_processes=N` (other constants in that string are kept; Default removes only this one). This is separate from `bg_procs` (Background apps kept), which uses `device_config put activity_manager max_cached_processes`. They are two stores for the same limit and Android may prefer the device config one, so set only one of them. At boot the daemon applies it only when it is not Default.

An existing install gets the new keys on the next daemon start, with the values of its current preset.

The daemon runs as `u:r:su:s0`, so it needs a userdebug ROM or permissive SELinux.

## Build

Requirements: the Android SDK (build-tools 35, platform 34) and a JDK.

```sh
sh build.sh
```

The script runs aapt2, javac, d8, zipalign and apksigner and writes `RomTuner.apk`. It generates a throwaway signing key, `tuner.keystore`, on the first run and does not commit it. An APK signed with a different key must replace the system copy, or the old app must be uninstalled first.

## Install as a system app

```sh
adb root
adb shell mount -o remount,rw /
adb shell mkdir -p /system/app/RomTuner
adb push RomTuner.apk /system/app/RomTuner/
adb push device/tablet_tunerd.sh /system/bin/
adb push device/tablet_tunerd.rc /system/etc/init/
adb shell 'chmod 755 /system/bin/tablet_tunerd.sh; chmod 644 /system/app/RomTuner/RomTuner.apk /system/etc/init/tablet_tunerd.rc'
adb shell 'restorecon -R /system/app/RomTuner /system/bin/tablet_tunerd.sh /system/etc/init/tablet_tunerd.rc'
adb reboot
```

The boot animation option copies one of `bootanimation-gemini.zip` (the default), `bootanimation-arrow.zip`, `bootanimation-galaxy.zip`, `bootanimation-aurora.zip` or `bootanimation-stock.zip` from `/system/product/media` over `bootanimation.zip`. The image builder installs the gemini, arrow, stock and (with `--galaxy`) galaxy files; the aurora file is not in this repository. `boot-animation/bootanimation-gemini.zip` is the Pixel 10 Gemini animation (see `NOTICE-gemini.md`; `make_gemini_fit.py` rebuilds it from the original), `boot-animation/bootanimation-arrow.zip` is the ArrowOS animation, and `boot-animation/make_galaxy.py` renders the galaxy one. `build/build_image.py` in the parent directory installs all of this into a system image, so a manual install is only needed on a running ROM.
