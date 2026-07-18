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
/baconboltadmin givecarrot                    # players EAT this to join
```

Aliases: `/bb`, `/bbadmin`

## Play

1. Eat the Bacon Bolt carrot → shrink + mount pig
2. When **2+** players have joined, countdown starts
3. Follow the glowing trail, drive through gold boxes
4. Scoreboard spins and lands on an effect
5. First through the finish wins

## Power-ups

Configured in `config.yml` under `powerups.effects` (weight, potion, duration). Defaults: Speed, Blindness, Slow, Jump, Boost, Dizzy.

## Stats API

```yaml
stats:
  provider: none   # or local | http
  http:
    url: "https://your.api/v1/baconbolt/results"
```
