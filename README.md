# Android 16 on the Lenovo TB-X606V (Tab M10 FHD Plus, MT8768, Android 10 vendor, kernel 4.9.190)

LineageOS 23.2 (Android 16) GSI, `lineage_arm64_bgN`, build 2026-05-24, running on the stock Android 10 vendor with the 4.9 kernel.
Status: boots, runs smooth (reported as the smoothest of everything tried on this tablet), Bluetooth works with the default below.
Not verified here: camera, Wi-Fi 5 GHz throughput, Dolby, DRM level.

Device: A-only, `system` partition 0xf3800000 bytes (3.8 GiB), unlocked bootloader, TWRP in `recovery`. **Never flash recovery.**
Enter TWRP with Volume Up + Volume Down held together, the bootloader with Power + Volume Down from off.

## Why a plain GSI hangs on the boot animation
`system_server` aborts ~2 s after start and zygote restarts it in a loop (21 times in 5 min). Root cause, from logcat: `StorageManagerService`
-> `MediaCodecList` -> `OmxInfoBuilder` -> `AudioCapabilities::getDefaultFormat` hits a UBSan sub-overflow abort. The Android 10 vendor declares
`OMX.MTK.AUDIO.DECODER.MP2` with type `audio/mpeg-L2`, which Android 16 rejects ("Unsupported mediaType audio/mpeg-L2"), leaving its ranges
empty. `mediaserver32` dies on the same entry. See `docs/ANALYSIS.md`.

## What the image contains (`build/build_image.py`)
| Change | Why |
|--------|-----|
| `/system/bin/init` boots SELinux permissive (`patch_init.py`; original kept as `init.enforcing`) | cmdline `androidboot.selinux=permissive` is ignored; the user wants SELinux off |
| `persist.sys.bt.unsupported.commands=182` in `build.prop` | the "Treble Settings > Misc > Bluetooth workaround > MediaTek" option, as a ROM default |
| `ro.debuggable=1`, `ro.secure=0`, `persist.sys.usb.config=adb` | rooted adb from first boot (`--no-debug` to skip) |
| `libhwui.so` patched (64 and 32-bit), `patches/patch_libhwui.py` | Android 16 seals bitmap memfds with `F_SEAL_FUTURE_WRITE` (Linux 5.1+); on kernel 4.9 every bitmap over 16 KB sent through binder fails with "Could not copy bitmap to parcel blob", which crashed YouTube. `docs/BitmapParcelTest.java` reproduces it |
| `--with-tuner`: ROM Tuner app + root daemon, ArrowOS boot animation (default), stock animation, optional galaxy | see `rom-tuner/README.md`; the daemon seeds the Smooth preset |
| `--dolby DIR`: Dolby Atmos apps (proprietary, supply your own) | Treble Settings > Audio effects must stay off: it attaches five effects (including DAP and Dynamics Processing) to every playback session and app audio cuts out after a few ms |
| `bootdiag` service, logs to `/cache/bootdiag` (`--no-bootdiag` to skip) | `/data` is FBE-encrypted, unreadable in TWRP; remove once stable |

The codec fix is on `/vendor`, so it is not in the system image: `patches/fix_vendor_codecs.py` (run from TWRP), or flash a vendor image that has it.

## Build and flash
```sh
# GSI: LineageOS 23.2 GAPPS EXT4 arm64 bgN (xz -d it first; it must be a raw ext4 image)
python3 build/build_image.py --gsi LineageOS-23.2-20260524-GAPPS-EXT4-GSI.img --out system.img --with-tuner   # needs e2fsprogs (debugfs, e2fsck, resize2fs); add --dolby DIR for Dolby
./flash.sh system.img            # system only; wipes userdata/metadata/cache; tablet in the bootloader
# then, BEFORE the first Android boot, in TWRP:
python3 patches/fix_vendor_codecs.py
```
Without the vendor fix the first boot loops on the boot animation. Boot takes several minutes; with the debug props adb appears once `system_server` is up.

## If it hangs
Reboot into TWRP and read `/cache/bootdiag/logcat.txt*` (and `/sys/fs/pstore/console-ramoops`, which is flooded by an MTK RT-scheduler debug print).
Restore: flash a saved `system.img` (this repo does not store images).

No license has been chosen. Scripts are original work; the GSI, Lenovo vendor image and Google/Lineage binaries are not included.
