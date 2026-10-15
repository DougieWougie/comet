# Comet

Network speed test for Android, themed as a comet rushing through space. The current test is a
rotating planet; ping, download and upload are three moons orbiting it. Tap the planet to start
or stop. Tap the bubble at the top left for network details.

## Layout

- `app/` – Android app (Kotlin, Compose, minSdk 31)
- `server/` – containerised LibreSpeed backend for a VPS behind Nginx Proxy Manager

## Server

```sh
cd server
echo NPM_NETWORK=<network nginx proxy manager is on> > .env
docker compose up -d --build
```

Then follow `server/nginx-proxy-manager.md` (disabling buffering matters for accurate numbers).
The container runs LibreSpeed in `backend` mode, so `empty.php` / `garbage.php` are served at
the root and the server URL in the app is simply `https://speed.example.com/`.

## App

```sh
./gradlew -PcometServerUrl=https://speed.example.com/ :app:assembleDebug   # default server
./gradlew :app:testDebugUnitTest
android run --apks=app/build/outputs/apk/debug/app-debug.apk
```

The server can also be changed at runtime in Settings. Debug builds allow cleartext `http://`
(for a local container); release builds require HTTPS.

### How it measures

- **Ping / jitter:** 10 sequential requests to `empty.php` after one discarded warm-up
  request; ping is the median, jitter the mean absolute difference between consecutive samples.
- **Throughput:** 5 parallel HTTP/1.1 connections for 10 s per direction. The first 2 s
  (TCP slow start) are discarded; the result is the 10 % trimmed mean of 250 ms window rates.

### Permissions

| Permission | When asked |
|---|---|
| `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `INTERNET` | install time |
| `ACCESS_FINE_LOCATION` | only when tapping "Show Wi-Fi name"; declining leaves just "Wi-Fi" |
| `READ_PHONE_STATE` | only when tapping "Show 4G/5G" on cellular |

### Privacy

The public IP lookup (ISP, ASN, city) calls ipinfo.io and so sends the device's IP to a third
party. It only runs when you tap "Look up public IP", and the UI says so beside the button and
in Settings. The speed-test server necessarily sees your IP too.

### Known limits

- With a VPN as the default network, Android reports no `WifiInfo`, so Wi-Fi details are hidden.
- On 5G NSA, `TelephonyManager` can report "4G".
