<div align="center">

# Lenovo Tab M10 FHD Plus · Android 16

**TB-X606V · LineageOS 23.2 GSI · MediaTek MT8768**

![Android](https://img.shields.io/badge/Android-16-green)
![Device](https://img.shields.io/badge/device-TB--X606V-blue)
![Status](https://img.shields.io/badge/status-experimental-orange)

[**Download ROM**](https://github.com/manvindar/tb-x606v-android16-port/releases/tag/rom-a16-full-v1) · [**ROM Tuner**](rom-tuner/README.md) · [**Changelog**](CHANGELOG.md) · [**Builds**](https://github.com/manvindar/tb-x606v-android16-port/actions)

</div>

> [!WARNING]
> For **Lenovo TB-X606V only**. The flashing script formats userdata and erases metadata/cache. Back up personal files and stock partitions off-device first. This is an experimental personal-device ROM: SELinux is permissive and the published image enables root USB debugging without host authorization.

## At a glance

| Component | Configuration |
| :--- | :--- |
| Android | Android 16 / LineageOS 23.2 GSI, May 24, 2026 build |
| Hardware | MT8768, PowerVR GE8320 |
| Base firmware | Android 10 Vendor and kernel 4.9.190 |
| Layout | A-only; System partition `0xf3800000` bytes |
| Recovery | Existing TWRP in Recovery; the flash script does not replace it |
| Included tooling | Compatibility patches, image builder and ROM Tuner |

Lenovo's older Vendor and kernel need compatibility fixes to run this GSI. The port addresses the vendor codec boot hang, bitmap parcel crashes, Bluetooth defaults and audio-effect interruptions. See the [technical analysis](docs/ANALYSIS.md).

## Downloads

The [full ROM release](https://github.com/manvindar/tb-x606v-android16-port/releases/tag/rom-a16-full-v1) contains:

| File | Purpose |
| :--- | :--- |
| `system-a16-tb-x606v.img.xz` | Complete compressed System image with compatibility fixes, ROM Tuner, ArrowOS animation and Dolby Atmos |
| `vendor-fixed-tb-x606v.img.xz` | Vendor image with MP2 codec fix, VP9 removal and Dolby audio files |
| `SHA256SUMS.txt` | Download checksums |

**Published ROM and source builds differ.** The current build workflow produces System only, with no Dolby apps or Vendor image. Source builds default to the Gemini animation; the existing full release retains ArrowOS. Lenovo/Dolby components remain subject to their owners' terms; the existing release describes its full-ROM assets as for private use.

[ROM Tuner v1.1](https://github.com/manvindar/tb-x606v-android16-port/releases/tag/rom-tuner-v1.1) is a separate app release. Current tuner source has later changes; rebuilding it does not reproduce the old ROM release byte-for-byte.

## Installation

1. Back up your data and stock System/Vendor with TWRP; keep a working recovery route.
2. Download the complete images and `SHA256SUMS.txt`. Verify downloads with `sha256sum -c SHA256SUMS.txt` (macOS: `shasum -a 256 -c SHA256SUMS.txt`), then decompress with `xz -d`. Never flash `.xz` files directly.
3. Enter the bootloader with Power + Volume Down while powered off. Connect the tablet to a computer with Android platform-tools.
4. From this repository, choose one route:

```sh
# Full release: System plus the supplied patched Vendor
./flash.sh system-a16-tb-x606v.img vendor-fixed-tb-x606v.img

# Alternative: System only; requires the Vendor codec fix below
./flash.sh system-a16-tb-x606v.img
```

> [!IMPORTANT]
> Both commands **wipe userdata**. The script writes System and, if supplied, Vendor; it does not write Boot, Recovery or VBMETA. For the System-only route, enter TWRP before the first Android boot and run `python3 patches/fix_vendor_codecs.py` from the computer. Without the Vendor codec fix, Android stays at the boot animation.

First boot takes several minutes. Keep Treble Settings audio effects off; the tuner daemon normally handles this. To return to stock, restore your verified backups and matching firmware rather than mixing images from different devices.

## Testing status

These are recorded observations from the maintained tablet, not a fresh test of every source revision.

| Area | Recorded status |
| :--- | :--- |
| Boot and launcher | Observed working |
| Wi-Fi | Observed working; 5 GHz throughput not verified |
| Bluetooth | Observed working with the MediaTek workaround |
| Speaker / YouTube | Audio and hardware H.264 playback observed |
| Dolby / ROM Tuner | Apps observed working; optional components vary by build |
| Camera | Not verified |
| Cellular | Not verified |
| DRM streaming level | Not verified |

Current-source compatibility, long-term stability and performance gains across all workloads are not established. The [tuner notes](rom-tuner/README.md) contain specific historical measurements and limits.

## ROM Tuner

The app and root daemon expose profiles, CPU/GPU settings, memory and network controls, appearance, boot animation and system actions. Some settings affect clocks or thermal limits; review the [tuner documentation](rom-tuner/README.md) before changing them. Its source includes optional overclock controls; their presence is not proof of safe operation on every tablet.

## Build from source

Requirements: Python 3 and e2fsprogs (`debugfs`, `e2fsck`, `resize2fs`). Extract the pinned LineageOS GSI first:

```sh
python3 build/build_image.py \
  --gsi LineageOS-23.2-20260524-GAPPS-EXT4-GSI.img \
  --out system.img \
  --with-tuner
```

On macOS, add `--e2fs /opt/homebrew/opt/e2fsprogs/sbin` when needed. The builder edits ext4 offline with `debugfs`, without mounting it. Patch scripts check input hashes and refuse mismatched builds.

The [build workflow](.github/workflows/build-rom.yml) downloads and verifies the GSI, builds System and uploads the compressed image. Optional Dolby inputs come from your own firmware; the workflow does not provide them. See [earlier build options](docs/HISTORICAL-SETUP.md#build-the-image).

## Source and troubleshooting

[Image builder](build/build_image.py) · [Flash script](flash.sh) · [Boot analysis](docs/ANALYSIS.md) · [Earlier setup notes](docs/HISTORICAL-SETUP.md) · [Release notes](docs/RELEASE_NOTES.md)

For a boot hang, use TWRP to inspect `/cache/bootdiag/logcat.txt*` and `/sys/fs/pstore/console-ramoops` when present. For audio that cuts out, disable Treble Settings audio effects. Preserve Recovery and device identity partitions.

Based on MisterZtr's LineageOS GSI, LineageOS, TrebleDroid and phh-treble work. Gemini animation attribution is in [its notice](rom-tuner/boot-animation/NOTICE-gemini.md). No license has been selected for the repository scripts. The [Android 17 port](https://github.com/manvindar/tb-x606v-android17) is a separate project; its fixes and validation do not automatically apply here.
