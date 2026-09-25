#!/usr/bin/env bash
# Cheque Watch setup: deploys the Edge Function, tests it, builds the watch app
# and installs it on the watch over wireless ADB.
#
#   Windows (PowerShell/cmd):  .\setup.cmd            (runs this with Git Bash)
#   macOS / Linux / Git Bash:  bash setup.sh
#
#   ... install   only (re)install the already-built APK on the watch
#
# Safe to re-run (e.g. after pulling updates): it reuses the existing key.
set -euo pipefail
cd "$(dirname "$0")"

PROPS="watch-app/local.properties"
APK="watch-app/app/build/outputs/apk/release/app-release.apk"

# --- Platform paths ------------------------------------------------------------
if command -v cygpath >/dev/null 2>&1; then          # Windows, Git Bash
  SDK_DIR="$(cygpath -m "${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}")"
  ADB="$SDK_DIR/platform-tools/adb.exe"
  STUDIO_JBR="/c/Program Files/Android/Android Studio/jbr"
elif [ "$(uname)" = "Darwin" ]; then                 # macOS
  SDK_DIR="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
  ADB="$SDK_DIR/platform-tools/adb"
  STUDIO_JBR="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
else                                                 # Linux
  SDK_DIR="${ANDROID_HOME:-$HOME/Android/Sdk}"
  ADB="$SDK_DIR/platform-tools/adb"
  STUDIO_JBR="${STUDIO_JBR:-/opt/android-studio/jbr}"
fi
# Gradle needs Java 17+; Android Studio's bundled JDK is a safe choice.
if [ -d "$STUDIO_JBR" ]; then export JAVA_HOME="$STUDIO_JBR"; fi

supabase() { npx --yes supabase@latest "$@"; }
step() { printf '\n\033[1;36m== %s\033[0m\n' "$*"; }
fail() { printf '\n\033[1;31m%s\033[0m\n' "$*"; exit 1; }
prop() { [ -f "$PROPS" ] && grep -E "^$1=" "$PROPS" | head -1 | cut -d= -f2- || true; }

if [ "${1:-}" != "install" ]; then

command -v node >/dev/null || fail "Node.js is required (for npx supabase). Install it from nodejs.org."
[ -d "$SDK_DIR" ] || fail "Android SDK not found at $SDK_DIR. Install Android Studio or set ANDROID_HOME."

# --- 1. Project + key --------------------------------------------------------
step "Supabase project"
echo "Your project ref is the xxxx in https://xxxx.supabase.co (Dashboard > Project Settings)."
DEFAULT_REF="$(prop watch.endpoint | sed -n 's#^https://\([a-z0-9]*\)\.supabase\.co.*#\1#p')"
REF=""
while [ -z "$REF" ]; do
  read -rp "Project ref${DEFAULT_REF:+ [$DEFAULT_REF]}: " REF
  REF="${REF:-$DEFAULT_REF}"
done
URL="https://$REF.supabase.co/functions/v1/watch-today"

KEY="$(prop watch.key)"
if [ -z "$KEY" ]; then
  KEY="$(node -e "console.log(require('crypto').randomBytes(32).toString('hex'))")"
  echo "Generated a new watch key."
else
  echo "Reusing the watch key from $PROPS."
fi

# --- 2. Deploy -----------------------------------------------------------------
step "Logging in to Supabase (a browser window may open; approve it)"
supabase projects list >/dev/null 2>&1 || supabase login

step "Saving secrets"
SECRETS=("WATCH_KEY=$KEY")
read -rp "Does your Cheque Tracker have more than one login? [y/N]: " MULTI
if [[ "$MULTI" =~ ^[Yy] ]]; then
  echo "Find your user id in Dashboard > Authentication > Users."
  read -rp "User id whose cheques the watch should show: " UID_IN
  if [ -n "$UID_IN" ]; then SECRETS+=("WATCH_USER_ID=$UID_IN"); fi
fi
supabase secrets set --project-ref "$REF" "${SECRETS[@]}"

step "Deploying the watch-today function"
supabase functions deploy watch-today --project-ref "$REF" --no-verify-jwt --use-api \
  || supabase functions deploy watch-today --project-ref "$REF" --no-verify-jwt

# --- 3. Test -------------------------------------------------------------------
step "Testing the function"
TMP_JSON="$(mktemp)"
CODE=000
for attempt in 1 2 3 4 5 6; do
  sleep 3
  CODE=$(curl -s -o "$TMP_JSON" -w '%{http_code}' -H "x-watch-key: $KEY" "$URL" || echo 000)
  [ "$CODE" = "200" ] && break
  echo "  got $CODE, retrying…"
done
BODY="$(cat "$TMP_JSON")"; rm -f "$TMP_JSON"
[ "$CODE" = "200" ] || fail "Function test failed ($CODE): $BODY
Check Dashboard > Edge Functions > watch-today > Logs."
NOKEY=$(curl -s -o /dev/null -w '%{http_code}' "$URL")
node -e '
  const d = JSON.parse(process.argv[1]);
  console.log(`  OK: ${d.date}: ${d.count} cheque(s) today, needed ${d.amount_needed}, ${d.overdue_count} overdue`);
' "$BODY"
echo "  Without the key the function answers $NOKEY (should be 401)."

# --- 4. Build ------------------------------------------------------------------
step "Building the watch app"
# .properties format: a ':' in a value (Windows drive letter) must be escaped.
BS='\'
SDK_PROP="${SDK_DIR//:/${BS}:}"
printf 'sdk.dir=%s\nwatch.endpoint=%s\nwatch.key=%s\n' "$SDK_PROP" "$URL" "$KEY" > "$PROPS"
(cd watch-app && bash ./gradlew --no-daemon -q assembleRelease)
echo "  Built $APK"

fi  # end of deploy/build part (skipped by "install")
[ -f "$APK" ] || fail "No APK at $APK. Run the setup without 'install' first."
[ -f "$ADB" ] || fail "adb not found at $ADB (Android SDK platform-tools)."

# --- 5. Install ----------------------------------------------------------------
step "Installing on the watch"
SERIALS=()
while read -r serial state; do
  if [ "$state" = "device" ]; then SERIALS+=("$serial"); fi
done < <("$ADB" devices | tail -n +2 | tr -d '\r')

if [ "${#SERIALS[@]}" -gt 0 ]; then
  # The same watch often shows up twice (IP:port and an mDNS name); only ask
  # when the connected devices are actually different models.
  MODELS="$(for s in "${SERIALS[@]}"; do "$ADB" -s "$s" shell getprop ro.product.model 2>/dev/null | tr -d '\r'; echo; done | sort -u | grep -c . || true)"
  PICK="${SERIALS[0]}"
  if [ "$MODELS" -gt 1 ]; then
    echo "Several devices are connected:"
    for i in "${!SERIALS[@]}"; do
      echo "  $((i + 1))) ${SERIALS[$i]}  $("$ADB" -s "${SERIALS[$i]}" shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
    done
    read -rp "Which one is the watch? [1]: " N
    PICK="${SERIALS[$(( ${N:-1} - 1 ))]}"
  fi
  echo "Using connected device $PICK."
  TARGET=(-s "$PICK")
else
  cat <<'EOF'
On the watch:
  1. Enable Developer options: Settings > About watch > Software information >
     tap "Software version" 7 times (wording varies slightly by brand)
  2. Connect the watch to the same Wi-Fi as this computer
  3. Settings > Developer options > turn ON "ADB debugging" and "Wireless debugging"
  4. Open "Wireless debugging" > "Pair new device"
Tip: tap the watch screen now and then so it stays on; the code stops working
as soon as the pairing popup closes.
EOF
  while true; do
    read -rp "IP:port shown under 'Pair new device' (e.g. 192.168.1.50:37123): " PAIR
    read -rp "Pairing code: " PCODE
    OUT="$("$ADB" pair "$PAIR" "$PCODE" 2>&1 || true)"
    echo "$OUT"
    if echo "$OUT" | grep -q "Successfully paired"; then break; fi
    echo
    echo "Pairing failed. On the watch, open 'Pair new device' again: it shows a NEW"
    echo "port and code. Enter them right away, keeping the screen on."
  done
  while true; do
    echo "Now go back one screen on the watch (Wireless debugging); it shows"
    echo "'IP address & Port' for connecting (a different port from pairing)."
    read -rp "That port (e.g. 41234): " CPORT
    CONN="${PAIR%:*}:${CPORT##*:}"
    OUT="$("$ADB" connect "$CONN" 2>&1 || true)"
    echo "$OUT"
    if echo "$OUT" | grep -q "connected to"; then break; fi
    echo "Couldn't connect. Check the port on the Wireless debugging screen and try again."
  done
  TARGET=(-s "$CONN")
fi
"$ADB" "${TARGET[@]}" install -r "$APK"

step "Done"
cat <<'EOF'
On the watch:
  - Turn OFF "Wireless debugging" and "ADB debugging" (saves battery).
  - Open the "Cheques" app once.
  - Add the tile: swipe left from the watch face, long-press a tile, scroll
    to the end, tap +, choose "Today's cheques".
EOF
