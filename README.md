# PigRace

Lounge pig-leash races for **Paper 26.1.2+**. Players click a join item, shrink down, get a leashed pig, and race to a finish line. Paths rotate each round. Max **4** racers. Spectators are intentionally allowed to interfere.

## Features

- Join by clicking a configurable item (or `/pigrace join`)
- Tiny racers (`Attribute.SCALE`) with leashed pigs
- Multiple path presets per lounge — a random ready path is picked each round
- Non-participants can mess with pigs / block routes (no spectator protection)
- Pluggable stats / leaderboard providers for a future website API

## Build

```bash
./gradlew build
```

Jar: `build/libs/PigRace-1.0.0.jar`  
Requires **JDK 25+**.

## Install

1. Drop the jar into `plugins/`
2. Restart on Paper 26.1.2+
3. Set up a lounge arena (below)
4. `/pigraceadmin givejoinitem` and place the item in the lounge

## Admin setup

```
/pigraceadmin create lounge
/pigraceadmin setlobby lounge
/pigraceadmin setexit lounge

/pigraceadmin createpath lounge red
/pigraceadmin addspawn lounge red          # stand at each pad (up to 4)
/pigraceadmin setfinish lounge red a       # finish corner A
/pigraceadmin setfinish lounge red b       # finish corner B

/pigraceadmin createpath lounge blue       # more paths = more variety
/pigraceadmin addspawn lounge blue
/pigraceadmin setfinish lounge blue a
/pigraceadmin setfinish lounge blue b

/pigraceadmin setjoinarena lounge
/pigraceadmin givejoinitem
```

## Player commands

```
/pigrace join [arena]
/pigrace leave
/pigrace info
```

## Stats / leaderboard API (website-ready)

Stats are **not hard-coded**. Race results go through `StatsProvider`:

| `stats.provider` | Behavior |
|------------------|----------|
| `none` (default) | Discard results — game still works |
| `local` | Write to `plugins/PigRace/stats.yml` |
| `http` | POST JSON to `stats.http.url` |

When your website API is ready:

```yaml
stats:
  provider: http
  http:
    enabled: true
    url: "https://your.api/v1/pigrace/results"
    auth-header: "Bearer YOUR_TOKEN"
    mirror-local: true   # optional dual-write to stats.yml
```

Example JSON payload:

```json
{
  "game": "pigrace",
  "arena": "lounge",
  "path": "red",
  "startedAt": 0,
  "endedAt": 0,
  "reason": "complete",
  "placements": [
    {"uuid":"…","name":"Steve","place":1,"timeSeconds":42.1,"finished":true}
  ]
}
```

`/pigraceadmin stats` shows the local leaderboard when `provider: local` (or mirrored).

## Config knobs

- `max-players` (capped at 4)
- `player-scale` / `pig-scale`
- `lobby-countdown-seconds`, `start-countdown-seconds`, `race-timeout-seconds`
- `join-item.*` material, name, lore, model, target arena
