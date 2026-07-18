# Bacon Bolt — Agent Guide: Website / Custom API Hookup

This document is for a **Cursor agent** (or developer) wiring Bacon Bolt race results into a custom website / club API.

Do **not** hard-code leaderboards inside the plugin. Stats already go through a pluggable provider.

---

## Current architecture (do not rip out)

| Piece | Path | Role |
|-------|------|------|
| Provider interface | `src/main/java/dev/genesi/baconbolt/stats/StatsProvider.java` | Contract: `record(RaceResult)`, optional `leaderboard()` |
| Factory | `.../stats/StatsService.java` | Builds provider from `config.yml` → `stats.provider` |
| No-op | `.../stats/NoOpStatsProvider.java` | Default — game works, nothing stored |
| Local YAML | `.../stats/LocalFileStatsProvider.java` | Dev / desk fallback → `plugins/BaconBolt/stats.yml` |
| HTTP | `.../stats/HttpApiStatsProvider.java` | POSTs JSON to your API (async, fails soft) |
| Payload model | `.../stats/RaceResult.java` | Immutable race outcome + placements |

Race code only calls `plugin.getStatsService().record(result)` when a race ends. Keep that call site.

---

## Config switch (server)

In `plugins/BaconBolt/config.yml`:

```yaml
stats:
  provider: http          # none | local | http
  local:
    file: stats.yml
  http:
    enabled: true
    url: "https://YOUR_DOMAIN/api/v1/baconbolt/results"
    method: POST
    auth-header: "Bearer YOUR_TOKEN"
    headers: {}
    timeout-seconds: 5
    include-placements: true
    mirror-local: true    # optional dual-write to stats.yml
```

After changing config: `/baconboltadmin reload` or restart.

---

## JSON payload the plugin already sends

`HttpApiStatsProvider` posts roughly:

```json
{
  "game": "baconbolt",
  "arena": "lounge",
  "path": "main",
  "startedAt": 1710000000000,
  "endedAt": 1710000120000,
  "reason": "complete",
  "placements": [
    {
      "uuid": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
      "name": "Steve",
      "place": 1,
      "timeSeconds": 42.1,
      "finished": true
    }
  ]
}
```

`reason` values today: `complete`, `timeout`, `force-stop`, `shutdown`, `no-path`, `arena-missing`.

---

## What the website agent should build

1. **API endpoint**  
   - `POST /api/v1/baconbolt/results` (or whatever URL you put in config)  
   - Auth via `Authorization` header matching `stats.http.auth-header`  
   - Validate JSON, upsert players by UUID, append race history  

2. **Storage**  
   - Prefer Postgres / your club DB — not Minecraft YAML  
   - Tables sketch: `baconbolt_races`, `baconbolt_placements`, `baconbolt_players` (wins, best_time, races)

3. **Public leaderboard page**  
   - Read from API/DB only  
   - Sort by wins / best time  
   - Do **not** scrape the Minecraft server  

4. **Optional later**  
   - `GET /api/v1/baconbolt/leaderboard?arena=&limit=`  
   - Implement `StatsProvider.leaderboard()` in HTTP provider to call that GET (currently returns “not configured”)  
   - In-game `/baconboltadmin stats` can then show live web data  

5. **Keep plugin config as the source of truth for the URL**  
   - Never bake production URLs into Java source  

---

## Suggested agent task checklist

- [ ] Create API route accepting the JSON above  
- [ ] Persist races + placements by UUID  
- [ ] Leaderboard UI (wins, best time, races)  
- [ ] Set Club server `stats.provider: http` + URL + bearer token  
- [ ] Run a test race and confirm a row appears on the site  
- [ ] (Optional) Implement leaderboard GET + wire `HttpApiStatsProvider.leaderboard()`  
- [ ] (Optional) Discord webhook on win using `reason: complete` + place 1  

---

## In-game join flow (for context)

1. Player clicks **join block** (`/baconboltadmin setjoinblock <arena>`) → receives carrot  
2. Player **eats** carrot → joins race  
3. At 2+ players → countdown on pads → GO  
4. Progressive trail (racers only) + power-up boxes  
5. First across finish ends race (`end-on-first-finish: true`) → `StatsService.record(...)`  

---

## Do not

- Hard-code a website leaderboard inside the plugin GUI  
- Replace `StatsProvider` with direct HTTP calls scattered in `GameManager`  
- Commit API secrets into the git repo — use server `config.yml` / env only  

---

## Quick test without a website

```yaml
stats:
  provider: local
```

Then check `plugins/BaconBolt/stats.yml` after a race. Switch to `http` when the API is ready.
