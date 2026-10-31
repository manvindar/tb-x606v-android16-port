#!/bin/sh
# Runs the per app compile block of device/tablet_tunerd.sh against stubs of cmd, pm, am and dumpsys (sh, no device needed).
# Usage: sh tests/test_apps.sh   (from rom-tuner/)
D=$(cd "$(dirname "$0")/.." && pwd); T=$(mktemp -d); fail=0
ok() { if [ "$2" = "$3" ]; then echo "ok   $1"; else echo "FAIL $1: got [$2] want [$3]"; fail=1; fi; }
log() { echo "$*" >> $T/log; }
load() { :; }
publish() { { cat $PST 2>/dev/null; cut -d' ' -f2 $QF 2>/dev/null | tr '\n' ,; echo; } >> $T/pub; }
oneof() { v_=$1; shift; for x_ in "$@"; do [ "$v_" = "$x_" ] && return 0; done; return 1; }
cstat() { cat $T/cstat 2>/dev/null || echo idle; }
APP=$T/app; mkdir -p $APP; LOCK=$T/lock
sleep() { command sleep 0.05; }
rm() { case "$*" in */data/dalvik-cache/*) echo "$*" >> $T/rm;; *) command rm "$@";; esac; }
pm() { case "$2" in *missing*) return 1;; esac; echo "package:/system/app/X/X.apk"; }
am() { echo "$*" >> $T/am; }
dumpsys() { :; }
cmd() {   # package compile ... | package art dump [pkg]
  case "$2" in
    compile) echo "$*" >> $T/compile; command sleep 0.3; case "$*" in *bad.app*) echo "Failure"; return 1;; esac; echo Success;;
    art) cat <<'DUMP'
[com.example.one]
  path: /data/app/~~x/com.example.one-y/base.apk
    arm64: [status=speed-profile] [reason=bg-dexopt] [primary-abi]
      [location is /data/app/~~x/com.example.one-y/oat/arm64/base.odex]
  path: /data/app/~~x/com.example.one-y/split_config.apk
    arm64: [status=verify] [reason=install] [primary-abi]
[com.example.two]
  path: /system/app/Two/Two.apk
    arm64: [status=verify] [reason=first-boot] [primary-abi]
      [location is /data/dalvik-cache/arm64/system@app@Two@Two.apk@classes.vdex]
[com.example.nocode]
  path: /system/app/N/N.apk
[com.example.three]
    arm64: [status=run-from-apk] [reason=unknown] [primary-abi]
DUMP
    ;;
  esac
}
eval "$(sed -n '/^# ---------- per app compile/,/^# ---------- state ----------/p' $D/device/tablet_tunerd.sh)"
QF=$T/q; QL=$T/ql; RL=$T/rl; PST=$T/pst; AFD=$T/afd; AFAIL=$T/afail

ok "parse: first status per package, no code skipped" "$(cmd package art dump | dexstates | tr '\n' ' ')" "com.example.one=speed-profile com.example.two=verify com.example.three=run-from-apk "
ok "parse: indented header (dumpsys form)" "$(cmd package art dump | sed 's/^/  /' | dexstates | head -1)" "com.example.one=speed-profile"
ok "parse: empty input" "$(echo 'Unknown command: art' | dexstates)" ""
pkgok com.example.one && r=yes || r=no; ok "name ok" $r yes
for n in com.x\;reboot -rf a com..x .com.x com.x. 1abc.def 'a b.c' ''; do pkgok "$n" && r=yes || r=no; ok "name rejected [$n]" $r no; done

: > $APP/apps_state
apps_refresh
ok "full refresh file" "$(tr '\n' ' ' < $AFD | sed 's/apps_state_time=[0-9]* $//')" "com.example.one=speed-profile com.example.two=verify com.example.three=run-from-apk "
ok "last line is the time marker" "$(tail -1 $AFD | cut -d= -f1)" apps_state_time
ok "copy for the app" "$(cmp -s $AFD $APP/apps_state && echo same)" same
ok "refresh lock released" "$([ -d $RL ] && echo held || echo free)" free

pkg_enqueue speed com.example.two; pkg_enqueue speed-profile com.example.one; pkg_enqueue speed-profile com.example.one
pkg_enqueue speed bad.app; pkg_enqueue reset com.example.missing
command sleep 3
ok "jobs ran in order, duplicate and missing skipped" "$(tr '\n' ';' < $T/compile)" "package compile -m speed -f com.example.two;package compile -m speed-profile -f com.example.one;package compile -m speed -f bad.app;"
ok "failure is reported" "$(cat $PST)" "bad.app:failed"
ok "force-stop only after a good compile" "$(tr '\n' ';' < $T/am)" "force-stop com.example.two;force-stop com.example.one;"
ok "queue empty, worker flag cleared" "$([ -s $QF ] && echo left || echo empty)/$([ -d $QL ] && echo flag || echo clear)" empty/clear
echo running > $T/cstat; pkg_enqueue speed com.example.two
ok "refused while a system compile runs" "$(cat $PST)/$(wc -l < $T/compile | tr -d ' ')" "com.example.two:failed/3"
command rm -rf $T
[ $fail = 0 ] && echo "all passed"; exit $fail
