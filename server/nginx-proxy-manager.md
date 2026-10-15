# Nginx Proxy Manager settings

Create a Proxy Host:

| Field | Value |
|---|---|
| Domain Names | `speed.example.com` |
| Scheme / Forward Host / Port | `http` / `comet-speedtest` / `8080` |
| Websockets Support | off (not needed) |
| SSL | Request a Let's Encrypt certificate, Force SSL on |

Under **Advanced → Custom Nginx Configuration** paste the following. Without it nginx
buffers the 100 MB downloads and the uploads, which makes the measured speed reflect
nginx's buffers rather than the network, and caps uploads at 1 MB by default.

```nginx
client_max_body_size 0;
proxy_buffering off;
proxy_request_buffering off;
proxy_http_version 1.1;
proxy_read_timeout 120s;
gzip off;
```

Enter `https://speed.example.com/` as the server in the Comet app's settings.
