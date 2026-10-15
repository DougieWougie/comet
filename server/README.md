# Comet speed-test server

LibreSpeed in backend mode (`empty.php`, `garbage.php`, `getIP.php`), behind Nginx Proxy Manager.
Follows the `/opt/docker/<app>/` pattern: no published ports, joins the shared `proxy` network,
image pinned.

## Deploy

1. DNS: add an `A` record for `comet.dougals.me`.
2. Copy this folder to the VPS as `/opt/docker/comet-speedtest/`.
3. Start it (the `proxy` network must already exist: `docker network create proxy`):

   ```sh
   cd /opt/docker/comet-speedtest
   docker compose up -d
   docker compose ps          # wait for "healthy"
   ```

4. Add the proxy host in NPM as described in [nginx-proxy-manager.md](nginx-proxy-manager.md).
   The custom nginx config there is required for accurate results.
5. Check it, then enter `https://comet.dougals.me/` in the app's Settings:

   ```sh
   curl -s https://comet.dougals.me/getIP.php
   ```

## Maintenance

- Update: change the image tag in `docker-compose.yml`, then `docker compose pull && docker compose up -d`.
- Logs: `docker compose logs -f`.
- Stateless: nothing to back up.

## Local test

Create an untracked `compose.override.yml` to publish a port, then point a debug build of the
app at it (`-PcometServerUrl=http://<host-ip>:18080/`):

```yaml
services:
  comet-speedtest:
    ports: ["18080:8080"]
```
