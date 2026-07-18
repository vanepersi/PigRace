package dev.genesi.pigrace.manager;

import dev.genesi.pigrace.PigRacePlugin;
import dev.genesi.pigrace.model.Arena;
import dev.genesi.pigrace.model.RacePath;
import dev.genesi.pigrace.model.RaceSession;
import dev.genesi.pigrace.powerup.PowerUpBox;
import dev.genesi.pigrace.powerup.PowerUpEffect;
import dev.genesi.pigrace.powerup.RaceScoreboard;
import dev.genesi.pigrace.stats.RaceResult;
import dev.genesi.pigrace.util.ScaleUtil;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class GameManager {

    private final PigRacePlugin plugin;
    private final RaceScoreboard scoreboards = new RaceScoreboard();
    private final Map<String, RaceSession> byArena = new HashMap<>();
    private final Map<UUID, RaceSession> byPlayer = new HashMap<>();

    public GameManager(PigRacePlugin plugin) {
        this.plugin = plugin;
    }

    public RaceScoreboard getScoreboards() {
        return scoreboards;
    }

    public Optional<RaceSession> getByPlayer(UUID uuid) {
        return Optional.ofNullable(byPlayer.get(uuid));
    }

    public Optional<RaceSession> getByArena(String arenaName) {
        return Optional.ofNullable(byArena.get(arenaName.toLowerCase()));
    }

    public boolean isPlaying(UUID uuid) {
        return byPlayer.containsKey(uuid);
    }

    public boolean tryJoin(Player player, Arena arena) {
        if (byPlayer.containsKey(player.getUniqueId())) {
            plugin.getMessageService().send(player, "already-playing");
            return false;
        }
        if (!arena.isReady()) {
            plugin.getMessageService().send(player, "arena-not-ready", Map.of("arena", arena.getName()));
            return false;
        }
        if (!arena.areWorldsLoaded()) {
            plugin.getMessageService().sendRaw(player, "&cArena world is not loaded yet. Try again in a moment.");
            return false;
        }

        int max = Math.max(1, Math.min(4, plugin.getConfig().getInt("max-players", 4)));
        RaceSession existing = byArena.get(arena.getName());
        if (existing != null && existing.getPhase() != RaceSession.Phase.WAITING) {
            plugin.getMessageService().sendRaw(player, "&cA race is already in progress in &e" + arena.getName() + "&c.");
            return false;
        }
        if (existing != null && existing.size() >= max) {
            plugin.getMessageService().send(player, "arena-full", Map.of("max", String.valueOf(max)));
            return false;
        }

        final RaceSession session;
        if (existing == null) {
            RaceSession created = new RaceSession(arena.getName());
            created.setLobbySecondsLeft(plugin.getConfig().getInt("lobby-countdown-seconds", 8));
            byArena.put(arena.getName(), created);
            created.setTickTask(Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(created), 20L, 20L));
            created.setFastTask(Bukkit.getScheduler().runTaskTimer(plugin, () -> fastTick(created), 1L, 1L));
            session = created;
        } else {
            session = existing;
        }

        RaceSession.Racer racer = session.addRacer(player);
        byPlayer.put(player.getUniqueId(), session);

        Location lobby = arena.getLobby();
        if (lobby != null) {
            player.teleport(lobby);
        }

        plugin.getMessageService().send(player, "joined", Map.of(
                "arena", arena.getName(),
                "count", String.valueOf(session.size()),
                "max", String.valueOf(max)
        ));
        plugin.getMessageService().send(player, "joining-anim");
        plugin.getMessageService().send(player, "interfere-hint");

        playJoinAnimation(player, racer, lobby != null ? lobby : player.getLocation());

        int min = Math.max(1, plugin.getConfig().getInt("min-players", 2));
        if (session.size() < min) {
            plugin.getMessageService().send(player, "need-more-players", Map.of(
                    "needed", String.valueOf(min - session.size())
            ));
        } else if (session.getPhase() == RaceSession.Phase.WAITING) {
            broadcast(session, "lobby-start", Map.of("seconds", String.valueOf(session.getLobbySecondsLeft())));
        }
        updateActionBars(session);
        return true;
    }

    /**
     * Shrink animation then mount the player on a pig.
     */
    private void playJoinAnimation(Player player, RaceSession.Racer racer, Location at) {
        racer.setAnimating(true);
        racer.setPreviousScale(ScaleUtil.getScale(player));

        double targetPlayer = plugin.getConfig().getDouble("player-scale", 0.28);
        double targetPig = plugin.getConfig().getDouble("pig-scale", 0.55);
        int duration = Math.max(10, plugin.getConfig().getInt("join-animation.duration-ticks", 30));
        Particle particle = parseParticle(plugin.getConfig().getString("join-animation.particle", "CLOUD"), Particle.CLOUD);

        Pig pig = (Pig) at.getWorld().spawnEntity(at.clone().add(0.3, 0, 0.3), EntityType.PIG);
        pig.setAdult();
        pig.setAI(true);
        pig.setAware(true);
        pig.setInvulnerable(true);
        pig.customName(LegacyComponentSerializer.legacyAmpersand().deserialize("&d" + player.getName() + "'s Pig"));
        pig.setCustomNameVisible(true);
        ScaleUtil.setScale(pig, 0.2);
        racer.setPig(pig);

        AtomicInteger tick = new AtomicInteger();
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!player.isOnline() || racer.getPig() == null || !racer.getPig().isValid()) {
                racer.setAnimating(false);
                task.cancel();
                return;
            }
            int t = tick.incrementAndGet();
            double progress = Math.min(1.0, t / (double) duration);
            ScaleUtil.setScale(player, lerp(1.0, targetPlayer, progress));
            ScaleUtil.setScale(pig, lerp(0.2, targetPig, progress));
            player.getWorld().spawnParticle(particle, player.getLocation().add(0, 1, 0), 6, 0.25, 0.4, 0.25, 0.01);

            if (t >= duration) {
                ScaleUtil.setScale(player, targetPlayer);
                ScaleUtil.setScale(pig, targetPig);
                mountPlayer(player, pig);
                giveSteerItem(player);
                player.playSound(player.getLocation(),
                        parseSound(plugin.getConfig().getString("join-animation.sound", "ENTITY_PLAYER_LEVELUP"), Sound.ENTITY_PLAYER_LEVELUP),
                        1f, 1.3f);
                racer.setAnimating(false);
                task.cancel();
            }
        }, 1L, 1L);
    }

    private void mountPlayer(Player player, Pig pig) {
        if (pig == null || !pig.isValid()) {
            return;
        }
        if (player.getVehicle() != null) {
            player.leaveVehicle();
        }
        pig.addPassenger(player);
    }

    private void giveSteerItem(Player player) {
        ItemStack steer = plugin.getItemFactory().createSteerItem();
        // Prefer empty hotbar slot 0
        player.getInventory().setItem(0, steer);
        player.getInventory().setHeldItemSlot(0);
    }

    public void leave(Player player, boolean announce) {
        RaceSession session = byPlayer.get(player.getUniqueId());
        if (session == null) {
            if (announce) {
                plugin.getMessageService().send(player, "not-playing");
            }
            return;
        }
        removePlayer(session, player, announce);
    }

    public void forceStop(Arena arena) {
        RaceSession session = byArena.get(arena.getName());
        if (session == null) {
            return;
        }
        endRace(session, "force-stop");
    }

    public void shutdown() {
        for (RaceSession session : List.copyOf(byArena.values())) {
            endRace(session, "shutdown");
        }
        byArena.clear();
        byPlayer.clear();
    }

    public void tryRemount(Player player) {
        if (!plugin.getConfig().getBoolean("auto-remount", true)) {
            return;
        }
        RaceSession session = byPlayer.get(player.getUniqueId());
        if (session == null || session.getPhase() == RaceSession.Phase.ENDED) {
            return;
        }
        RaceSession.Racer racer = session.getRacer(player.getUniqueId());
        if (racer == null || racer.isFinished() || racer.isAnimating()) {
            return;
        }
        Pig pig = racer.getPig();
        if (pig == null || !pig.isValid()) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && (player.getVehicle() == null || !player.getVehicle().equals(pig))) {
                mountPlayer(player, pig);
            }
        }, 2L);
    }

    private void tick(RaceSession session) {
        if (session.getPhase() == RaceSession.Phase.ENDED) {
            return;
        }
        Optional<Arena> arenaOpt = plugin.getArenaManager().get(session.getArenaName());
        if (arenaOpt.isEmpty()) {
            endRace(session, "arena-missing");
            return;
        }
        Arena arena = arenaOpt.get();

        switch (session.getPhase()) {
            case WAITING -> tickWaiting(session, arena);
            case COUNTDOWN -> tickCountdown(session);
            case RACING -> tickRacing(session);
            default -> {
            }
        }
        updateActionBars(session);
    }

    private void fastTick(RaceSession session) {
        if (session.getPhase() == RaceSession.Phase.ENDED) {
            return;
        }
        if (session.getPhase() == RaceSession.Phase.RACING || session.getPhase() == RaceSession.Phase.COUNTDOWN) {
            rotatePowerUps(session);
            if (session.getPhase() == RaceSession.Phase.RACING) {
                checkPowerUpCollects(session);
                showTrail(session);
            }
        }
        maintainMounts(session);
    }

    private void tickWaiting(RaceSession session, Arena arena) {
        int min = Math.max(1, plugin.getConfig().getInt("min-players", 2));
        if (session.size() < min) {
            session.setLobbySecondsLeft(plugin.getConfig().getInt("lobby-countdown-seconds", 8));
            return;
        }
        int left = session.getLobbySecondsLeft() - 1;
        session.setLobbySecondsLeft(left);
        if (left <= 0) {
            beginCountdown(session, arena);
        } else if (left <= 5 || left % 5 == 0) {
            broadcast(session, "lobby-start", Map.of("seconds", String.valueOf(left)));
        }
    }

    private void beginCountdown(RaceSession session, Arena arena) {
        Optional<RacePath> pathOpt = arena.pickRandomReadyPath();
        if (pathOpt.isEmpty()) {
            broadcastRaw(session, "&cNo ready race paths — cancelling.");
            endRace(session, "no-path");
            return;
        }
        RacePath path = pathOpt.get();
        session.setActivePath(path);
        session.setPhase(RaceSession.Phase.COUNTDOWN);
        session.setStartSecondsLeft(plugin.getConfig().getInt("start-countdown-seconds", 3));
        broadcast(session, "path-picked", Map.of("path", path.getName()));

        spawnPowerUpBoxes(session, path);

        List<Location> spawns = path.getSpawns();
        int index = 0;
        for (RaceSession.Racer racer : session.getRacers().values()) {
            Player player = Bukkit.getPlayer(racer.getUuid());
            if (player == null || !player.isOnline()) {
                continue;
            }
            Location spawn = spawns.get(Math.min(index, spawns.size() - 1));
            teleportRacer(player, racer, spawn);
            ensurePigReady(player, racer, spawn);
            giveSteerItem(player);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 10, 10, false, false, false));
            index++;
        }
    }

    private void teleportRacer(Player player, RaceSession.Racer racer, Location spawn) {
        Pig pig = racer.getPig();
        if (pig != null && pig.isValid()) {
            player.leaveVehicle();
            pig.teleport(spawn);
            mountPlayer(player, pig);
        } else {
            player.teleport(spawn);
        }
    }

    private void ensurePigReady(Player player, RaceSession.Racer racer, Location spawn) {
        Pig pig = racer.getPig();
        if (pig == null || !pig.isValid()) {
            pig = (Pig) spawn.getWorld().spawnEntity(spawn, EntityType.PIG);
            pig.setAdult();
            pig.setInvulnerable(true);
            pig.customName(LegacyComponentSerializer.legacyAmpersand().deserialize("&d" + player.getName() + "'s Pig"));
            pig.setCustomNameVisible(true);
            ScaleUtil.setScale(pig, plugin.getConfig().getDouble("pig-scale", 0.55));
            ScaleUtil.setScale(player, plugin.getConfig().getDouble("player-scale", 0.28));
            racer.setPig(pig);
            mountPlayer(player, pig);
        }
    }

    private void tickCountdown(RaceSession session) {
        int left = session.getStartSecondsLeft() - 1;
        session.setStartSecondsLeft(left);
        if (left > 0) {
            broadcast(session, "countdown", Map.of("seconds", String.valueOf(left)));
            playSound(session, Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
            return;
        }
        startRacing(session);
    }

    private void startRacing(RaceSession session) {
        session.setPhase(RaceSession.Phase.RACING);
        session.setRaceStartMillis(System.currentTimeMillis());
        session.setRaceSecondsLeft(plugin.getConfig().getInt("race-timeout-seconds", 180));
        broadcast(session, "go", Map.of());
        playSound(session, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);

        String pathName = session.getActivePath() == null ? "?" : session.getActivePath().getName();
        for (UUID uuid : session.getRacers().keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.removePotionEffect(PotionEffectType.SLOWNESS);
                scoreboards.showRaceHud(player, pathName, session.getRaceSecondsLeft());
            }
        }
    }

    private void tickRacing(RaceSession session) {
        int left = session.getRaceSecondsLeft() - 1;
        session.setRaceSecondsLeft(left);

        checkFinishes(session);
        showFinishParticles(session);
        respawnPowerUps(session);

        String pathName = session.getActivePath() == null ? "?" : session.getActivePath().getName();
        for (UUID uuid : session.getRacers().keySet()) {
            if (session.isSpinning(uuid)) {
                continue;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                scoreboards.showRaceHud(player, pathName, left);
            }
        }

        if (session.getPlacements().size() >= session.size()) {
            endRace(session, "complete");
            return;
        }
        if (left <= 0) {
            if (session.getPlacements().isEmpty()) {
                broadcast(session, "timeout", Map.of());
            }
            endRace(session, "timeout");
        }
    }

    private void maintainMounts(RaceSession session) {
        if (!plugin.getConfig().getBoolean("ride-pigs", true)) {
            return;
        }
        for (RaceSession.Racer racer : session.getRacers().values()) {
            if (racer.isFinished() || racer.isAnimating()) {
                continue;
            }
            Player player = Bukkit.getPlayer(racer.getUuid());
            Pig pig = racer.getPig();
            if (player == null || pig == null || !pig.isValid()) {
                continue;
            }
            if (player.getVehicle() == null || !player.getVehicle().getUniqueId().equals(pig.getUniqueId())) {
                if (session.getPhase() == RaceSession.Phase.WAITING
                        || session.getPhase() == RaceSession.Phase.COUNTDOWN
                        || session.getPhase() == RaceSession.Phase.RACING) {
                    mountPlayer(player, pig);
                }
            }
        }
    }

    private void spawnPowerUpBoxes(RaceSession session, RacePath path) {
        clearPowerUpBoxes(session);
        if (!plugin.getConfig().getBoolean("powerups.enabled", true)) {
            return;
        }
        Material material = plugin.getPowerUpRegistry().boxMaterial();
        float scale = (float) plugin.getConfig().getDouble("powerups.display-scale", 0.55);
        List<Location> spots = path.getPowerUps();
        for (int i = 0; i < spots.size(); i++) {
            Location loc = spots.get(i).clone().add(0, 1.0, 0);
            PowerUpBox box = new PowerUpBox(i, loc);
            World world = loc.getWorld();
            if (world == null) {
                continue;
            }
            ItemDisplay display = world.spawn(loc, ItemDisplay.class, d -> {
                d.setItemStack(new ItemStack(material));
                d.setInterpolationDuration(1);
                d.setTeleportDuration(1);
            });
            box.setDisplay(display);
            box.applyTransform(0f, scale);
            session.getActiveBoxes().add(box);
        }
    }

    private void clearPowerUpBoxes(RaceSession session) {
        for (PowerUpBox box : session.getActiveBoxes()) {
            box.remove();
        }
        session.getActiveBoxes().clear();
    }

    private void rotatePowerUps(RaceSession session) {
        if (session.getActiveBoxes().isEmpty()) {
            return;
        }
        float deg = (float) plugin.getConfig().getDouble("powerups.rotate-degrees-per-tick", 6);
        float amp = (float) plugin.getConfig().getDouble("powerups.bob-amplitude", 0.12);
        float scale = (float) plugin.getConfig().getDouble("powerups.display-scale", 0.55);
        long now = System.currentTimeMillis();
        for (PowerUpBox box : session.getActiveBoxes()) {
            if (!box.isAvailable()) {
                continue;
            }
            ItemDisplay display = box.getDisplay();
            if (display == null || display.isDead()) {
                continue;
            }
            box.addYaw(deg);
            float bob = (float) (Math.sin(now / 180.0 + box.getIndex()) * amp);
            box.applyTransform(bob, scale);
        }
    }

    private void respawnPowerUps(RaceSession session) {
        long now = System.currentTimeMillis();
        float scale = (float) plugin.getConfig().getDouble("powerups.display-scale", 0.55);
        for (PowerUpBox box : session.getActiveBoxes()) {
            if (box.isAvailable()) {
                continue;
            }
            if (now < box.getRespawnAtMillis()) {
                continue;
            }
            box.setAvailable(true);
            ItemDisplay display = box.getDisplay();
            if (display != null && !display.isDead()) {
                display.setItemStack(new ItemStack(plugin.getPowerUpRegistry().boxMaterial()));
                box.applyTransform(0f, scale);
            }
        }
    }

    private void checkPowerUpCollects(RaceSession session) {
        if (!plugin.getConfig().getBoolean("powerups.enabled", true)) {
            return;
        }
        double radius = plugin.getConfig().getDouble("powerups.collect-radius", 1.35);
        double r2 = radius * radius;
        for (RaceSession.Racer racer : session.getRacers().values()) {
            if (racer.isFinished() || session.isSpinning(racer.getUuid())) {
                continue;
            }
            Player player = Bukkit.getPlayer(racer.getUuid());
            if (player == null) {
                continue;
            }
            Location probe = player.getLocation();
            Pig pig = racer.getPig();
            if (pig != null && pig.isValid()) {
                probe = pig.getLocation();
            }
            for (PowerUpBox box : session.getActiveBoxes()) {
                if (!box.isAvailable()) {
                    continue;
                }
                if (box.getBase().getWorld() == null || probe.getWorld() == null) {
                    continue;
                }
                if (!box.getBase().getWorld().equals(probe.getWorld())) {
                    continue;
                }
                if (box.getBase().distanceSquared(probe) > r2) {
                    continue;
                }
                collectPowerUp(session, player, box);
                break;
            }
        }
    }

    private void collectPowerUp(RaceSession session, Player player, PowerUpBox box) {
        box.setAvailable(false);
        int respawnSeconds = plugin.getConfig().getInt("powerups.respawn-seconds", 8);
        box.setRespawnAtMillis(System.currentTimeMillis() + respawnSeconds * 1000L);
        ItemDisplay display = box.getDisplay();
        if (display != null && !display.isDead()) {
            display.setItemStack(new ItemStack(Material.AIR));
        }
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, box.getBase(), 20, 0.3, 0.4, 0.3, 0.1);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1.4f);

        PowerUpEffect chosen = plugin.getPowerUpRegistry().pickWeighted();
        if (chosen == null) {
            return;
        }
        startRoulette(session, player, chosen);
    }

    private void startRoulette(RaceSession session, Player player, PowerUpEffect chosen) {
        session.setSpinning(player.getUniqueId(), true);
        String title = plugin.getConfig().getString("powerups.spin.title", "&6&lITEM BOX");
        int duration = Math.max(10, plugin.getConfig().getInt("powerups.spin.duration-ticks", 40));
        int interval = Math.max(1, plugin.getConfig().getInt("powerups.spin.tick-interval", 2));
        AtomicInteger elapsed = new AtomicInteger();

        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!player.isOnline() || session.getPhase() == RaceSession.Phase.ENDED) {
                session.setSpinning(player.getUniqueId(), false);
                task.cancel();
                return;
            }
            int t = elapsed.addAndGet(interval);
            if (t < duration) {
                PowerUpEffect flash = plugin.getPowerUpRegistry().pickRandomForSpin();
                if (flash != null) {
                    scoreboards.showSpin(player, title, flash.display(), false);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.6f + (t / (float) duration));
                }
            } else {
                scoreboards.showSpin(player, title, chosen.display(), true);
                player.addPotionEffect(new PotionEffect(
                        chosen.potion(),
                        chosen.durationTicks(),
                        chosen.amplifier(),
                        false,
                        true,
                        true
                ));
                plugin.getMessageService().send(player, "powerup-got", Map.of("effect", chosen.display()));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.8f);
                session.setSpinning(player.getUniqueId(), false);
                // restore race HUD shortly after
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline() && session.getPhase() == RaceSession.Phase.RACING && !session.isSpinning(player.getUniqueId())) {
                        String pathName = session.getActivePath() == null ? "?" : session.getActivePath().getName();
                        scoreboards.showRaceHud(player, pathName, session.getRaceSecondsLeft());
                    }
                }, 40L);
                task.cancel();
            }
        }, 0L, interval);
    }

    private void showTrail(RaceSession session) {
        if (!plugin.getConfig().getBoolean("trail.enabled", true)) {
            return;
        }
        RacePath path = session.getActivePath();
        if (path == null) {
            return;
        }
        List<Location> points = new ArrayList<>();
        List<Location> spawns = path.getSpawns();
        if (!spawns.isEmpty()) {
            points.add(spawns.getFirst().clone().add(0, 0.2, 0));
        }
        for (Location trail : path.getTrail()) {
            points.add(trail.clone().add(0, plugin.getConfig().getDouble("trail.y-offset", 0.35), 0));
        }
        Location finish = path.finishCenter();
        if (finish != null) {
            points.add(finish.clone().add(0, 0.3, 0));
        }
        if (points.size() < 2) {
            return;
        }
        Particle particle = parseParticle(plugin.getConfig().getString("trail.particle", "END_ROD"), Particle.END_ROD);
        int density = Math.max(1, plugin.getConfig().getInt("trail.density", 4));
        for (int i = 0; i < points.size() - 1; i++) {
            Location a = points.get(i);
            Location b = points.get(i + 1);
            if (a.getWorld() == null || b.getWorld() == null || !a.getWorld().equals(b.getWorld())) {
                continue;
            }
            Vector step = b.toVector().subtract(a.toVector()).multiply(1.0 / (density + 1));
            Location cursor = a.clone();
            for (int s = 0; s < density; s++) {
                cursor.add(step);
                a.getWorld().spawnParticle(particle, cursor, 1, 0.02, 0.02, 0.02, 0);
            }
        }
    }

    private void checkFinishes(RaceSession session) {
        RacePath path = session.getActivePath();
        if (path == null) {
            return;
        }
        double radius = plugin.getConfig().getDouble("finish-check-radius-fallback", 2.5);
        boolean requirePig = plugin.getConfig().getBoolean("finish-requires-pig", false);

        for (RaceSession.Racer racer : session.getRacers().values()) {
            if (racer.isFinished()) {
                continue;
            }
            Player player = Bukkit.getPlayer(racer.getUuid());
            if (player == null) {
                continue;
            }
            boolean playerIn = path.contains(player.getLocation(), radius);
            Pig pig = racer.getPig();
            boolean pigIn = pig != null && pig.isValid() && path.contains(pig.getLocation(), radius);
            boolean done = requirePig ? (playerIn && pigIn) : (playerIn || pigIn);
            if (done) {
                markFinished(session, racer, player);
            }
        }
    }

    private void markFinished(RaceSession session, RaceSession.Racer racer, Player player) {
        racer.setFinished(true);
        double seconds = (System.currentTimeMillis() - session.getRaceStartMillis()) / 1000.0;
        int place = session.getPlacements().size() + 1;
        session.addPlacement(new RaceSession.Placement(racer.getUuid(), racer.getName(), place, seconds));

        broadcast(session, "finished", Map.of(
                "player", racer.getName(),
                "place", String.valueOf(place),
                "time", String.format("%.1f", seconds)
        ));
        if (place == 1) {
            broadcast(session, "win", Map.of("player", racer.getName()));
            playSound(session, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);

        if (session.getPlacements().size() >= session.size()) {
            endRace(session, "complete");
        }
    }

    private void showFinishParticles(RaceSession session) {
        RacePath path = session.getActivePath();
        if (path == null) {
            return;
        }
        Location center = path.finishCenter();
        if (center == null || center.getWorld() == null) {
            return;
        }
        center.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, center, 8, 0.8, 0.4, 0.8, 0.01);
    }

    private void endRace(RaceSession session, String reason) {
        if (session.getPhase() == RaceSession.Phase.ENDED) {
            return;
        }
        session.setPhase(RaceSession.Phase.ENDED);
        session.cancelTask();
        clearPowerUpBoxes(session);

        long ended = System.currentTimeMillis();
        List<RaceResult.Placement> placements = new ArrayList<>();
        int place = 1;
        for (RaceSession.Placement p : session.getPlacements()) {
            placements.add(new RaceResult.Placement(p.uuid(), p.name(), p.place(), p.timeSeconds(), true));
            place = Math.max(place, p.place() + 1);
        }
        for (RaceSession.Racer racer : session.getRacers().values()) {
            if (session.hasFinished(racer.getUuid())) {
                continue;
            }
            double seconds = session.getRaceStartMillis() <= 0
                    ? 0
                    : (ended - session.getRaceStartMillis()) / 1000.0;
            placements.add(new RaceResult.Placement(racer.getUuid(), racer.getName(), place++, seconds, false));
        }

        String pathName = session.getActivePath() == null ? "" : session.getActivePath().getName();
        plugin.getStatsService().record(new RaceResult(
                session.getArenaName(),
                pathName,
                session.getRaceStartMillis() <= 0 ? ended : session.getRaceStartMillis(),
                ended,
                reason,
                placements
        ));

        if (!"shutdown".equals(reason) && !"force-stop".equals(reason)) {
            broadcast(session, "race-ended", Map.of());
        }

        Optional<Arena> arenaOpt = plugin.getArenaManager().get(session.getArenaName());
        Location exit = arenaOpt.map(a -> {
            Location e = a.getExit();
            return e != null ? e : a.getLobby();
        }).orElse(null);

        for (UUID uuid : List.copyOf(session.getRacers().keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            RaceSession.Racer racer = session.getRacer(uuid);
            cleanupRacer(racer, player, exit);
            byPlayer.remove(uuid);
        }
        session.getRacers().clear();
        byArena.remove(session.getArenaName());
    }

    private void removePlayer(RaceSession session, Player player, boolean announce) {
        RaceSession.Racer racer = session.getRacer(player.getUniqueId());
        session.removeRacer(player.getUniqueId());
        byPlayer.remove(player.getUniqueId());

        Optional<Arena> arenaOpt = plugin.getArenaManager().get(session.getArenaName());
        Location exit = arenaOpt.map(a -> {
            Location e = a.getExit();
            return e != null ? e : a.getLobby();
        }).orElse(null);
        cleanupRacer(racer, player, exit);

        if (announce) {
            plugin.getMessageService().send(player, "left");
        }

        if (session.size() == 0) {
            session.setPhase(RaceSession.Phase.ENDED);
            session.cancelTask();
            clearPowerUpBoxes(session);
            byArena.remove(session.getArenaName());
            return;
        }

        if (session.getPhase() == RaceSession.Phase.RACING
                && session.getPlacements().size() >= session.size()) {
            endRace(session, "complete");
        }
    }

    private void cleanupRacer(RaceSession.Racer racer, Player player, Location exit) {
        if (player != null) {
            scoreboards.clear(player);
            player.leaveVehicle();
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            clearSteerItems(player);
            if (racer != null && racer.getPreviousScale() != null) {
                ScaleUtil.setScale(player, racer.getPreviousScale());
            } else {
                ScaleUtil.setScale(player, 1.0);
            }
            if (plugin.getConfig().getBoolean("teleport-on-end", true) && exit != null) {
                player.teleport(exit);
            }
        }
        if (racer != null && racer.getPig() != null) {
            Pig pig = racer.getPig();
            if (pig.isValid()) {
                pig.eject();
                pig.remove();
            }
        }
    }

    private void clearSteerItems(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (plugin.getItemFactory().isSteerItem(stack)) {
                player.getInventory().setItem(i, null);
            }
        }
    }

    private void updateActionBars(RaceSession session) {
        int max = Math.max(1, Math.min(4, plugin.getConfig().getInt("max-players", 4)));
        MessageService messages = plugin.getMessageService();
        for (UUID uuid : session.getRacers().keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            String template;
            Map<String, String> vars = new HashMap<>();
            vars.put("count", String.valueOf(session.size()));
            vars.put("max", String.valueOf(max));
            switch (session.getPhase()) {
                case WAITING -> {
                    template = plugin.getConfig().getString("action-bar-waiting", "");
                    vars.put("seconds", String.valueOf(session.getLobbySecondsLeft()));
                }
                case COUNTDOWN -> {
                    template = plugin.getConfig().getString("action-bar-countdown", "");
                    vars.put("seconds", String.valueOf(session.getStartSecondsLeft()));
                }
                case RACING -> {
                    template = plugin.getConfig().getString("action-bar-racing", "");
                    vars.put("time", String.valueOf(session.getRaceSecondsLeft()));
                    vars.put("path", session.getActivePath() == null ? "?" : session.getActivePath().getName());
                }
                default -> template = "";
            }
            if (template != null && !template.isBlank()) {
                messages.actionBar(player, messages.apply(template, vars));
            }
        }
    }

    private void broadcast(RaceSession session, String key, Map<String, String> placeholders) {
        for (UUID uuid : session.getRacers().keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                plugin.getMessageService().send(player, key, placeholders);
            }
        }
    }

    private void broadcastRaw(RaceSession session, String message) {
        for (UUID uuid : session.getRacers().keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                plugin.getMessageService().sendRaw(player, message);
            }
        }
    }

    private void playSound(RaceSession session, Sound sound, float volume, float pitch) {
        for (UUID uuid : session.getRacers().keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        }
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static Particle parseParticle(String name, Particle fallback) {
        if (name == null) {
            return fallback;
        }
        try {
            return Particle.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private static Sound parseSound(String name, Sound fallback) {
        if (name == null) {
            return fallback;
        }
        try {
            return Sound.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
