# Changelog

Android 16 port and ROM Tuner release history for **Lenovo TB-X606V**. Published releases and later source changes are distinct.

## 2026-11-18 · Documentation refresh

- Reorganized the README with download links, install routes, testing status and build guidance.
- Distinguished the full ROM's Dolby/Vendor/ArrowOS components from the current System-only workflow and Gemini default.
- Made data wiping, permissive SELinux and unauthenticated root USB debugging explicit.
- Preserved earlier technical and setup details in `docs/HISTORICAL-SETUP.md`.

Documentation only; no new image, flashing-script change or device test.

## 2026-10-24 · Android 16 full ROM v1

[Release: rom-a16-full-v1](https://github.com/manvindar/tb-x606v-android16-port/releases/tag/rom-a16-full-v1)

- Published the patched LineageOS 23.2 Android 16 System image for the Android 10 Vendor/kernel base.
- Included permissive init, the bitmap parcel fix, MediaTek Bluetooth defaults, rooted adb and ROM Tuner.
- Included ArrowOS boot animation and Dolby Atmos in this full-ROM asset.
- Published the patched Vendor image with MP2 codec removal, software VP9 removal and Dolby files, plus checksums.
- Documented installation through `flash.sh`, which wipes userdata and preserves Boot/Recovery/VBMETA.

Recorded tablet checks covered boot/launcher, Wi-Fi, Bluetooth, speaker audio, YouTube H.264 playback and the Dolby/Tuner apps. Camera, cellular, DRM level and 5 GHz throughput were not verified. This does not validate every later source revision.

## 2026-10-24 · ROM Tuner v1.1

[Release: rom-tuner-v1.1](https://github.com/manvindar/tb-x606v-android16-port/releases/tag/rom-tuner-v1.1)

- Added the flat performance-dial launcher icon with light, dark and themed variants.
- Retained the v1.0 app/daemon functionality according to the release notes.

## 2026-10-24 · ROM Tuner v1.0

[Release: rom-tuner-v1.0](https://github.com/manvindar/tb-x606v-android16-port/releases/tag/rom-tuner-v1.0)

Initial published ROM Tuner app release. See the [current tuner documentation](rom-tuner/README.md) for features and later source changes; it is not a frozen description of the v1.0 binary.
