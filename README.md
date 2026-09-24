# Netflared Server

Netflared Server is a Paper 26.2 plugin that automates the server side of a Cloudflare Tunnel for Minecraft.

The intended experience is:

1. Install the plugin.
2. Give it a scoped Cloudflare API token once.
3. Choose your Cloudflare domain.
4. Netflared creates the tunnel, configures TCP ingress, creates the DNS record, downloads cloudflared, and starts it.
5. Players use the companion Netflared client mod.

Repository: https://github.com/matejpcs/netflared-server

Client mod: https://github.com/matejpcs/netflared

---

## Features

- Paper 26.2
- Java 25
- Automatic remotely-managed Cloudflare Tunnel creation
- Automatic TCP ingress configuration for the Minecraft port
- Automatic CNAME creation/update
- Automatic cloudflared download from the official Cloudflare release
- Automatic cloudflared startup
- Automatic reconnect after an unexpected cloudflared exit
- Automatic Paper port detection
- Persistent tunnel configuration
- No inbound Minecraft port forwarding required
- Simple /netflared administration commands
- API setup token is not persisted
- Tunnel token is persisted for automatic restarts
- Clean cloudflared shutdown
- Linux and Windows x86/x64/ARM targets
- Small, separated components intended to support future server adapters

---

# Architecture

The server side looks like this:

~~~text
Minecraft server
      |
      | localhost:25565
      v
Netflared Paper plugin
      |
      | cloudflared tunnel run --token-file <token-file>
      v
Cloudflare Tunnel
      |
      | public hostname
      v
play.example.com
~~~

The client side is separate:

~~~text
Minecraft client
      |
      v
Netflared client mod
      |
      | cloudflared access tcp
      v
local temporary TCP port
      |
      v
Cloudflare Tunnel
      |
      v
Minecraft server
~~~

This is important: the server plugin does not try to make a normal Minecraft client speak directly to a Cloudflare TCP hostname. The companion client connector handles the client side.

Cloudflare's documented remote-tunnel workflow is to create a tunnel through the API, configure a public hostname, create a CNAME pointing at the tunnel, and run cloudflared using the tunnel token.

---

# Requirements

## Minecraft server

- Paper 26.2
- Java 25
- A Cloudflare account
- A domain/zone active on Cloudflare DNS
- Outbound Internet access
- HTTPS access to api.cloudflare.com
- Access to GitHub release downloads
- Outbound Cloudflare Tunnel connectivity

Paper documents Java 25 as the required Java generation for Paper 26.1+ and uses the 26.2.build.* API version scheme.

## Minecraft clients

Use the companion Netflared client mod:

https://github.com/matejpcs/netflared

Other Netflared client editions are maintained separately for older 1.21.x environments.

---

# Installation

1. Download the latest netflared-server JAR.
2. Stop the Paper server.
3. Put the JAR in:

~~~text
plugins/
~~~

4. Start Paper.
5. Confirm that Netflared loads successfully.
6. Create the Cloudflare API token described below.
7. Run:

~~~text
/netflared setup <cloudflare-api-token>
~~~

The plugin creates its data directory automatically:

~~~text
plugins/Netflared/
├── config.json
└── bin/
    └── cloudflared
~~~

Windows uses cloudflared.exe.

---

# Cloudflare API token

Netflared needs an API token during initial setup because it must create and configure Cloudflare resources.

Cloudflare recommends API tokens instead of legacy global API keys.

## Required permissions

Create a custom API token with:

| Scope | Permission | Purpose |
|---|---|---|
| Account | Cloudflare Tunnel: Edit | Create/configure the tunnel |
| Zone | DNS: Edit | Create/update the Minecraft CNAME |
| Zone | Zone: Read | List zones during setup |

Cloudflare's current Tunnel API documentation specifies Cloudflare Tunnel Edit plus DNS Edit for API-managed tunnels. The zones API requires Zone Read.

## Restrict the resources

For a least-privilege setup:

- restrict **Account Resources** to the account that owns the tunnel
- restrict **Zone Resources** to the Minecraft zone

Do not grant unrelated Cloudflare permissions.

Cloudflare supports resource restrictions on API tokens.

## Token handling

The setup API token is held only in memory and is not written to config.json.

The resulting tunnel token is stored because cloudflared needs it every time the server reconnects.

Treat the tunnel token as a password. Cloudflare documents that possession of a tunnel token allows someone to run the tunnel.

Do not publish config.json.

### Important command-line warning

The current setup command accepts the API token as a command argument:

~~~text
/netflared setup <token>
~~~

Depending on the host, console history or hosting logs may retain that command. The token is not persisted by Netflared, but the command itself can still be visible to an administrator or host.

A future release should provide safer secret input.

---

# First-time setup

Start with:

~~~text
/netflared setup <cloudflare-api-token>
~~~

Example:

~~~text
/netflared setup cfut_xxxxxxxxxxxxxxxxx
~~~

The plugin validates the token and lists available zones:

~~~text
Cloudflare token accepted. Choose a domain:

1. example.com
2. example.net

Run: /netflared setup select <number> [subdomain]
Default subdomain: play
~~~

Choose a domain:

~~~text
/netflared setup select 1
~~~

The default hostname becomes:

~~~text
play.example.com
~~~

Choose another subdomain:

~~~text
/netflared setup select 1 mc
~~~

Result:

~~~text
mc.example.com
~~~

Nested subdomains are also supported:

~~~text
/netflared setup select 1 survival.play
~~~

Result:

~~~text
survival.play.example.com
~~~

The plugin automatically reads Paper's configured server port.

## What setup does

~~~text
Validate API token
        ↓
List Cloudflare zones
        ↓
Select zone
        ↓
Create remote tunnel
        ↓
Retrieve tunnel token
        ↓
Configure TCP ingress
        ↓
Create/update CNAME
        ↓
Save local configuration
        ↓
Download cloudflared
        ↓
Start cloudflared
~~~

The Cloudflare ingress created by Netflared is equivalent to:

~~~text
hostname: play.example.com
service: tcp://localhost:25565
~~~

A final catch-all rule returns HTTP 404 for unmatched traffic, as required by Cloudflare ingress configuration.

---

# Commands

All commands require:

~~~text
netflared.admin
~~~

The permission defaults to server operators.

## Status

~~~text
/netflared status
~~~

Displays:

- public hostname
- local port
- tunnel ID
- current cloudflared state

## Info

~~~text
/netflared info
~~~

Displays:

- public hostname
- Minecraft port
- Cloudflare zone
- tunnel name
- detected platform
- local cloudflared path

## Connect

~~~text
/netflared connect
~~~

Downloads cloudflared if necessary and starts it.

## Disconnect

~~~text
/netflared disconnect
~~~

Stops the current cloudflared process.

## Reload

~~~text
/netflared reload
~~~

Reloads config.json, stops cloudflared, and starts it again if configured.

## Setup

~~~text
/netflared setup <token>
~~~

Starts a setup session.

Then:

~~~text
/netflared setup select <zone-number> [subdomain]
~~~

finishes the setup.

## Reset

~~~text
/netflared reset
~~~

Removes the local configuration and stops cloudflared.

It intentionally does **not** delete the Cloudflare Tunnel or DNS record. This prevents an accidental local reset from deleting a remote resource.

---

# Configuration

Example:

~~~json
{
  "enabled": true,
  "accountId": "...",
  "zoneId": "...",
  "zoneName": "example.com",
  "tunnelId": "...",
  "tunnelName": "netflared-myserver",
  "tunnelToken": "...",
  "hostname": "play.example.com",
  "serverPort": 25565
}
~~~

The most sensitive field is:

~~~text
tunnelToken
~~~

Protect the entire config.json file.

Do not put it in Git.

Do not post it in an issue.

Do not include it in screenshots.

---

# Networking

Cloudflare Tunnel is outbound from the server.

The basic path is:

~~~text
Minecraft server -> cloudflared -> Cloudflare
~~~

There is no requirement for:

~~~text
Internet -> server:25565
~~~

This is useful for:

- CGNAT
- residential Internet
- ISPs without public IPv4
- servers without port-forwarding access
- servers where direct inbound exposure is undesirable

Cloudflare documents outbound Tunnel connectivity on port 7844.

The server firewall therefore needs to permit the required outbound traffic.

---

# DNS

During setup, Netflared creates a CNAME similar to:

~~~text
play.example.com
        |
        v
<TUNNEL-ID>.cfargotunnel.com
~~~

The record is created as a proxied CNAME.

If a CNAME with the selected hostname already exists, Netflared updates that CNAME instead of creating a duplicate.

The plugin does not automatically delete unrelated DNS records.

---

# Tunnel lifecycle

The plugin uses a remotely managed tunnel.

At startup:

1. Load config.json.
2. Check whether a tunnel token exists.
3. Download cloudflared if missing.
4. Start:

~~~text
cloudflared tunnel run --token-file <token-file>
~~~

5. Monitor the process.

If cloudflared exits unexpectedly:

~~~text
cloudflared exits
       ↓
wait 5 seconds
       ↓
start again
~~~

When Paper shuts down, Netflared terminates the child process.

This means a cloudflared crash should not require a Minecraft server restart.

---

# Cloudflared downloads

Netflared does not package the large cloudflared executable inside the plugin JAR.

Instead, it queries the latest Cloudflare release and selects the asset for the detected platform.

Current targets:

| OS | Architecture |
|---|---|
| Linux | amd64 |
| Linux | arm64 |
| Linux | arm |
| Linux | 386 |
| Windows | amd64 |
| Windows | arm64 |
| Windows | 386 |

macOS is not enabled in the first implementation because the current macOS cloudflared releases are archive assets rather than directly executable files. macOS support can be added by extracting those archives.

The binary is downloaded into the plugin's data directory.

---

# Security model

Netflared uses two different credentials.

## API token

Purpose:

- list zones
- create tunnel
- configure tunnel
- create/update DNS

Lifetime:

- setup only
- not stored by the plugin

Recommended permissions:

- Cloudflare Tunnel Edit
- DNS Edit
- Zone Read

Recommended resource scope:

- one Cloudflare account
- one Cloudflare zone

## Tunnel token

Purpose:

- start the remotely managed cloudflared connector

Lifetime:

- persistent

Storage:

~~~text
plugins/Netflared/config.json
~~~

Anyone with the tunnel token can run the tunnel, so filesystem permissions matter.

For stronger security:

- run the Minecraft server as a dedicated OS user
- restrict access to plugins/Netflared
- use a dedicated Cloudflare account or zone when appropriate
- scope the API token narrowly
- consider token expiration/rotation
- never commit config.json
- never expose the tunnel token in logs

---

# Troubleshooting

## No Cloudflare zones are visible

Check:

- Zone Read permission
- zone resource scope
- domain is active in Cloudflare
- the API token is still active

The Cloudflare zones endpoint requires Zone Read.

## HTTP 403 from Cloudflare

The token was accepted but lacks permission for the requested operation.

Check:

- Cloudflare Tunnel Edit on the selected account
- DNS Edit on the selected zone
- Zone Read for setup

## cloudflared cannot download

Check:

- Internet connection
- DNS
- outbound HTTPS
- GitHub access
- CPU architecture
- operating system

You can inspect the expected platform with:

~~~text
/netflared info
~~~

## Tunnel is running but players cannot connect

Check:

1. /netflared status
2. Paper server port
3. selected Cloudflare hostname
4. Cloudflare DNS record
5. client Netflared mod
6. client-side cloudflared access connection

The tunnel service should be:

~~~text
tcp://localhost:<minecraft-port>
~~~

## Minecraft port changed

The port is captured during setup.

If the server port changes, re-run setup so the Cloudflare ingress is updated.

## Existing DNS record causes unexpected behavior

Check the selected hostname in Cloudflare DNS.

Netflared only manages the CNAME matching the selected hostname. It does not modify unrelated records.

## Setup partially fails

The current implementation creates the Cloudflare resources in sequence. If a later step fails, a tunnel may exist even if local configuration was not saved.

Check the Cloudflare dashboard before retrying. Future versions should add explicit rollback/recovery for partially completed setup.

---

# Development

## Requirements

- JDK 25
- Gradle 9.x
- Paper 26.2 API

Build:

~~~bash
gradle build
~~~

Tests:

~~~bash
gradle test
~~~

Output:

~~~text
build/libs/netflared-server-<version>.jar
~~~

The repository includes a GitHub Actions workflow that builds with Java 25 and Gradle 9.7.1.

## Project layout

~~~text
netflared-server/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── README.md
├── .gitignore
├── .github/
│   └── workflows/
│       └── build.yml
└── src/
    ├── main/
    │   ├── java/
    │   │   └── com/matejpcs/netflared/server/
    │   └── resources/
    │       └── plugin.yml
    └── test/
        └── java/
            └── com/matejpcs/netflared/server/
~~~

## Components

### NetflaredServerPlugin

Paper lifecycle and orchestration.

### NetflaredCommand

User-facing /netflared commands.

### ServerConfig

Persistent JSON configuration.

### CloudflareClient

Small Java HTTP client for the Cloudflare REST API.

Handles:

- token verification
- zone listing
- tunnel creation
- tunnel token retrieval
- tunnel configuration
- DNS lookup
- DNS creation/update/deletion
- tunnel deletion

### CloudflaredManager

Handles:

- platform detection
- release lookup
- binary download
- executable permissions
- process startup
- process shutdown
- reconnects

### Platform

Normalizes OS and CPU architecture.

### Hostname

Validates and constructs public hostnames.

---

# Design decisions

## Why a remote tunnel?

A remotely managed tunnel keeps the important ingress configuration in Cloudflare.

~~~text
Cloudflare Account
└── Tunnel
    ├── connector
    └── public hostname
        └── tcp://localhost:25565
~~~

The server only needs the connector token at runtime.

This is simpler for a non-technical server owner than maintaining a cloudflared YAML file.

## Why keep the API token out of config.json?

The API token can create and modify Cloudflare resources. The runtime connector only needs the tunnel token.

Discarding the API token after setup follows the principle of keeping the server's long-lived credential as narrow as practical.

## Why not automatically delete the tunnel on reset?

A local reset should not destroy remote infrastructure unexpectedly.

The reset command therefore stops cloudflared and removes local state only.

---

# Roadmap

Planned improvements:

- Safer API-token input that does not put the token in command history
- Automatic recovery from partially completed setup
- Explicit tunnel discovery/reuse
- Tunnel credential rotation
- Optional automatic cloudflared updates
- Better cloudflared health reporting
- Rich command completion
- Multiple tunnel profiles
- More automated tests
- macOS support
- Better DNS conflict detection
- Automatic configuration migration
- Optional client/server integration for automatic client profile discovery
- Additional server-platform adapters

---

# References

Paper:

- https://jd.papermc.io/paper/26.2/
- https://docs.papermc.io/paper/dev/project-setup/
- https://docs.papermc.io/paper/getting-started/

Cloudflare:

- https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/get-started/create-remote-tunnel-api/
- https://developers.cloudflare.com/tunnel/get-started/
- https://developers.cloudflare.com/tunnel/reference/tunnel-tokens/
- https://developers.cloudflare.com/api/resources/dns/subresources/records/methods/create/
- https://developers.cloudflare.com/fundamentals/api/get-started/create-token/

---

# Current status

This repository now contains the first functional Paper 26.2 implementation.

The basic architecture is intentionally in place before adding more features. The next work should focus on production hardening: safer credential entry, setup rollback/recovery, stronger automated tests, cloudflared version management, DNS conflict handling, and tunnel credential rotation.
