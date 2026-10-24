Patched system image: LineageOS 23.2 (Android 16) GSI, build 2026-05-24, for the Lenovo TB-X606V (MT8768, Android 10 vendor, kernel 4.9.190).

Included: init boots SELinux permissive, `libhwui` fix for the bitmap parcel crash, Bluetooth MediaTek workaround default, rooted adb from first boot,
ROM Tuner (presets, governor, overclock, thermal, I/O, memory, network, behaviour) with its root daemon, ArrowOS boot animation (default) plus stock and galaxy choices.
Not included: Dolby Atmos (proprietary), the vendor image.

Install (system only, wipes userdata; never flash recovery):
1. `xz -d system-a16-tb-x606v.img.xz`
2. Tablet in the bootloader (Power + Volume Down while off), then `./flash.sh system-a16-tb-x606v.img` from the repo.
3. Before the first Android boot, in TWRP (Volume Up + Volume Down), run `python3 patches/fix_vendor_codecs.py`. Without it system_server aborts on the vendor's MP2 codec entry and the boot animation never ends.
4. Treble Settings > Audio effects: keep every effect off, or app audio cuts out after a few milliseconds.
