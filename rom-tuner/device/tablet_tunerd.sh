#!/system/bin/sh
# ROM Tuner root daemon. The app writes "key=value" lines to <app files>/request; this applies them and keeps the
# master state in /data/local/tmp/tuner.state (survives "clear data"). Modes: no args = daemon, args = inotifyd handler.
S=/data/local/tmp/tuner.state
APP=/data/data/org.tablet.tuner/files
LOCK=/data/local/tmp/tuner.lock
M=/system/product/media
BLACK=org.lineageos.overlay.customization.blacktheme
log() { echo "$(date +%T) $*" >> /data/local/tmp/tuner.log; }

# ---------- state ----------
seed() {
cat > $S <<EOS
perf_profile=balanced
gpu_lock=1
cap_big=-1
cap_little=-1
night=yes
black_theme=1
palette_source=preset
accent=3E91FF
theme_style=TONAL_SPOT
font=lato
icon_shape=squircle
icon_pack=rounded
nav_mode=gestural
anim_scale=1.0
density=192
bootanim=galaxy
logging=0
EOS
}
load() { . $S; }
putkey() { grep -v "^$1=" $S > $S.t; echo "$1=$2" >> $S.t; mv $S.t $S; }
publish() {   # copy state (+ a status line) to the app; the file is pre-created with the app's owner/context
  [ -f $APP/state ] || return
  gpu_eff=$gpu_lock; [ "$perf_profile" = battery ] && gpu_eff=0
  { cat $S; echo "status=$perf_profile, GPU $([ "$gpu_eff" = 1 ] && echo locked 680 MHz || echo auto)"; } > $APP/state
}

# ---------- appliers ----------
w() { echo "$2" > "$1" 2>/dev/null || log "FAIL $1"; }
calc() {
  case $perf_profile in
    battery)     pm=1 fg=0  minb=-1      minl=-1;;
    performance) pm=3 fg=40 minb=2009000 minl=1491000;;
    *)           pm=0 fg=20 minb=1484000 minl=1050000;;
  esac
  gpu_eff=$gpu_lock; [ "$perf_profile" = battery ] && gpu_eff=0
  [ "$cap_big" != -1 ]    && [ "$minb" -gt "$cap_big" ]    && minb=$cap_big
  [ "$cap_little" != -1 ] && [ "$minl" -gt "$cap_little" ] && minl=$cap_little
}
profile() {
  calc
  w /proc/cpufreq/cpufreq_power_mode $pm
  w /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_fg_boost $fg
  w /proc/ppm/policy/userlimit_min_cpu_freq "0 -1"; w /proc/ppm/policy/userlimit_min_cpu_freq "1 -1"
  w /proc/ppm/policy/userlimit_max_cpu_freq "0 $cap_big"; w /proc/ppm/policy/userlimit_max_cpu_freq "1 $cap_little"
  w /proc/ppm/policy/userlimit_min_cpu_freq "0 $minb"; w /proc/ppm/policy/userlimit_min_cpu_freq "1 $minl"
  if [ "$gpu_eff" = 1 ]; then w /proc/gpufreq/gpufreq_opp_freq 680000; else w /proc/gpufreq/gpufreq_opp_freq 0; fi
}
theme() {
  K=android.theme.customization
  if [ "$palette_source" = home_wallpaper ]; then
    J="{\"$K.color_both\":\"1\",\"$K.color_source\":\"home_wallpaper\""
  else
    J="{\"$K.color_both\":\"0\",\"$K.color_source\":\"preset\",\"$K.system_palette\":\"$accent\",\"$K.accent_color\":\"$accent\""
  fi
  J="$J,\"$K.theme_style\":\"$theme_style\""
  case $font in
    default) ;; lato|rubik) J="$J,\"$K.font\":\"org.lineageos.overlay.font.$font\"";;
    *) J="$J,\"$K.font\":\"com.android.theme.font.$font\"";;
  esac
  [ "$icon_shape" != default ] && J="$J,\"$K.adaptive_icon_shape\":\"com.android.theme.icon.$icon_shape\""
  if [ "$icon_pack" != default ]; then
    for t in android launcher settings systemui; do J="$J,\"$K.icon_pack.$t\":\"com.android.theme.icon_pack.$icon_pack.$t\""; done
  fi
  settings put secure theme_customization_overlay_packages "$J,\"_applied_timestamp\":$(date +%s)000}"
  # the black overlay shares the palette category and gets dropped when the theme is rewritten
  ( sleep 6; black ) &
}
black() { cmd overlay $([ "$black_theme" = 1 ] && echo enable || echo disable) --user 0 $BLACK; }
nav() { cmd overlay enable-exclusive --user 0 --category android.theme.customization.navbar \
          com.android.internal.systemui.navbar.$([ "$nav_mode" = threebutton ] && echo threebutton || echo gestural); }
anim() { for k in window_animation_scale transition_animation_scale animator_duration_scale; do settings put global $k $anim_scale; done; }
density_() { if [ "$density" = reset ]; then wm density reset; else wm density $density; fi; }
bootanim() {
  mount -o remount,rw / 2>/dev/null
  cmp -s $M/bootanimation-$bootanim.zip $M/bootanimation.zip || { cp $M/bootanimation-$bootanim.zip $M/bootanimation.zip; chmod 644 $M/bootanimation.zip; restorecon $M/bootanimation.zip; }
}
logging() {
  if [ "$logging" = 1 ]; then setprop persist.log.tag ""; logcat -b all -G 1M
  else setprop persist.log.tag S; logcat -b all -G 64K; fi
}
night_() { cmd uimode night $([ "$night" = yes ] && echo yes || { [ "$night" = no ] && echo no || echo auto; }); }

# ---------- request handling ----------
handle() {
  while ! mkdir $LOCK 2>/dev/null; do sleep 0.3; done
  trap 'rmdir $LOCK' EXIT
  [ -f $APP/request ] || exit 0
  mv $APP/request $APP/request.work
  load; T=0; P=0
  while IFS== read -r k v; do
    case $v in ''|*[!A-Za-z0-9._-]*) log "reject $k"; continue;; esac
    log "req $k=$v"
    case $k in
      perf_profile|gpu_lock|cap_big|cap_little) P=1; putkey $k $v;;
      palette_source|accent|theme_style|font|icon_shape|icon_pack) T=1; putkey $k $v;;
      night|black_theme|nav_mode|anim_scale|density|bootanim|logging) putkey $k $v;;
      restart_ui) pkill -f com.android.systemui; continue;;
      reboot) sync; reboot; continue;;
      *) log "unknown $k"; continue;;
    esac
    load
    case $k in
      night) night_;; black_theme) black;; nav_mode) nav;; anim_scale) anim;; density) density_;;
      bootanim) bootanim;; logging) logging;;
    esac
  done < $APP/request.work
  rm -f $APP/request.work
  [ $P = 1 ] && profile
  [ $T = 1 ] && theme
  load; publish
}

# ---------- daemon ----------
[ -n "$1" ] && { case $1 in *w*) [ "$3" = request ] && handle;; esac; exit 0; }
rmdir $LOCK 2>/dev/null
[ -f $S ] || seed
until [ -d $(dirname $APP) ]; do sleep 2; done; mkdir -p $APP; chown $(stat -c %u $(dirname $APP)):$(stat -c %u $(dirname $APP)) $APP; chmod 771 $APP; restorecon $APP
UID_=$(stat -c %u $APP)
for f in state request; do [ -f $APP/$f ] || touch $APP/$f; chown $UID_:$UID_ $APP/$f; chmod 660 $APP/$f; restorecon $APP/$f; done
rm -f $APP/request                          # the app recreates it; inotifyd watches the directory
load; profile; night_; nav; anim; density_; bootanim; [ "$logging" = 1 ] && logging
( sleep 12; load; black; sleep 25; black ) &
# the MTK power HAL rewrites the min-clock limits on every touch boost and resets them to -1 afterwards: put ours back when that happens
( while sleep 8; do load; calc; [ "$perf_profile" = battery ] && continue
    grep -q "^0: min_freq_idx = -1" /proc/ppm/policy/userlimit_min_cpu_freq && { w /proc/ppm/policy/userlimit_min_cpu_freq "0 $minb"; w /proc/ppm/policy/userlimit_min_cpu_freq "1 $minl"; }
  done ) &
publish; log "daemon ready"
exec inotifyd /system/bin/tablet_tunerd.sh $APP:nw   # toybox mask parsing is quirky: single letters are rejected, "wn" drops w
