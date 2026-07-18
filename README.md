# Bacon Bolt

Lounge pig races for **Paper 26.1.2+**. Eat a carrot → shrink onto a pig → race the lounge trail. Mario Kart–style power-up boxes spin effects on your scoreboard. Starts at **2** players (max 4).

## Features

- **Eat** a join carrot to enter (shrink animation + sit on a pig)
- Steer with a carrot on a stick
- Trail waypoints light the route through your lounge
- Rotating gold power-up boxes → scoreboard roulette → Speed / Blindness / etc.
- Spectators can still interfere
- Pluggable stats API (`none` / `local` / `http`) for a future website

## Build

```bash
./gradlew build
```

Jar: `build/libs/BaconBolt-1.2.0.jar` — **JDK 25+**

## Admin setup

```
/baconboltadmin create lounge
/baconboltadmin setlobby lounge
/baconboltadmin setexit lounge

/baconboltadmin createpath lounge main
/baconboltadmin addspawn lounge main          # race pads (up to 4)
/baconboltadmin addtrail lounge main          # walk the route, repeat
/baconboltadmin addpowerup lounge main        # place Mario Kart boxes
/baconboltadmin setfinish lounge main a
/baconboltadmin setfinish lounge main b

/baconboltadmin setjoinarena lounge
/baconboltadmin setjoinblock lounge           # look at a block — click gives a carrot
/baconboltadmin givecarrot                    # optional manual carrot
```

Aliases: `/bb`, `/bbadmin`

Website / API hookup notes for agents: [`docs/AGENT_API_HOOKUP.md`](docs/AGENT_API_HOOKUP.md)

## Play

1. Click the join block → get a carrot → **eat** it to join
2. When **2+** players have joined, countdown starts (mounted on pads)
3. Follow the trail **portion ahead of you** (passed smoke clears)
4. Drive through gold boxes for scoreboard power-ups
5. First through the finish ends the race

## Power-ups

Configured in `config.yml` under `powerups.effects` (weight, potion, duration). Defaults: Speed, Blindness, Slow, Jump, Boost, Dizzy.

## Stats API

```yaml
stats:
  provider: none   # or local | http
  http:
    url: "https://your.api/v1/baconbolt/results"
```
