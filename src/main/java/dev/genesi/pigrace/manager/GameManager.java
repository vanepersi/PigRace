package dev.genesi.pigrace.manager;

import dev.genesi.pigrace.PigRacePlugin;
import dev.genesi.pigrace.model.Arena;
import dev.genesi.pigrace.model.RacePath;
import dev.genesi.pigrace.model.RaceSession;
import dev.genesi.pigrace.stats.RaceResult;
import dev.genesi.pigrace.util.ScaleUtil;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class GameManager {

    private final PigRacePlugin plugin;
    private final Map<String, RaceSession> byArena = new HashMap<>();
    private final Map<UUID, RaceSession> byPlayer = new HashMap<>();

    public GameManager(PigRacePlugin plugin) {
        this.plugin = plugin;
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
            created.setLobbySecondsLeft(plugin.getConfig().getInt("lobby-countdown-seconds", 15));
            byArena.put(arena.getName(), created);
            created.setTickTask(Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(created), 20L, 20L));
            session = created;
        } else {
            session = existing;
        }

        session.addRacer(player);
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
        plugin.getMessageService().send(player, "interfere-hint");

        int min = Math.max(1, plugin.getConfig().getInt("min-players", 1));
        if (session.size() >= min && session.getPhase() == RaceSession.Phase.WAITING) {
            broadcast(session, "lobby-start", Map.of("seconds", String.valueOf(session.getLobbySecondsLeft())));
        }
        updateActionBars(session);
        return true;
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

    private void tickWaiting(RaceSession session, Arena arena) {
        int min = Math.max(1, plugin.getConfig().getInt("min-players", 1));
        if (session.size() < min) {
            session.setLobbySecondsLeft(plugin.getConfig().getInt("lobby-countdown-seconds", 15));
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

        List<Location> spawns = path.getSpawns();
        int index = 0;
        for (RaceSession.Racer racer : session.getRacers().values()) {
            Player player = Bukkit.getPlayer(racer.getUuid());
            if (player == null || !player.isOnline()) {
                continue;
            }
            Location spawn = spawns.get(Math.min(index, spawns.size() - 1));
            player.teleport(spawn);
            applyRaceState(player, racer, spawn);
            index++;
        }
    }

    private void applyRaceState(Player player, RaceSession.Racer racer, Location spawn) {
        double playerScale = plugin.getConfig().getDouble("player-scale", 0.30);
        double pigScale = plugin.getConfig().getDouble("pig-scale", 0.45);

        racer.setPreviousScale(ScaleUtil.getScale(player));
        ScaleUtil.setScale(player, playerScale);

        Location pigLoc = spawn.clone().add(spawn.getDirection().normalize().multiply(1.2));
        Pig pig = (Pig) spawn.getWorld().spawnEntity(pigLoc, EntityType.PIG);
        pig.setAdult();
        pig.setAware(true);
        pig.setAI(true);
        pig.customName(LegacyComponentSerializer.legacyAmpersand().deserialize("&d" + player.getName() + "'s Pig"));
        pig.setCustomNameVisible(true);
        ScaleUtil.setScale(pig, pigScale);
        AttributeInstance speed = pig.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(Math.max(0.15, speed.getBaseValue() * 0.85));
        }
        pig.setLeashHolder(player);
        racer.setPig(pig);

        // Mild slow so they don't sprint away before GO
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 10, 10, false, false, false));
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
        session.setRaceSecondsLeft(plugin.getConfig().getInt("race-timeout-seconds", 120));
        broadcast(session, "go", Map.of());
        playSound(session, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);

        for (RaceSession.Racer racer : session.getRacers().values()) {
            Player player = Bukkit.getPlayer(racer.getUuid());
            if (player != null) {
                player.removePotionEffect(PotionEffectType.SLOWNESS);
            }
        }
    }

    private void tickRacing(RaceSession session) {
        int left = session.getRaceSecondsLeft() - 1;
        session.setRaceSecondsLeft(left);

        maintainPigs(session);
        checkFinishes(session);
        showFinishParticles(session);

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

    private void maintainPigs(RaceSession session) {
        double maxDist = plugin.getConfig().getDouble("leash-max-distance", 8.0);
        for (RaceSession.Racer racer : session.getRacers().values()) {
            if (racer.isFinished()) {
                continue;
            }
            Player player = Bukkit.getPlayer(racer.getUuid());
            if (player == null) {
                continue;
            }
            Pig pig = racer.getPig();
            if (pig == null || pig.isDead() || !pig.isValid()) {
                Location at = player.getLocation().add(player.getLocation().getDirection().normalize().multiply(1.0));
                Pig respawned = (Pig) player.getWorld().spawnEntity(at, EntityType.PIG);
                respawned.setAdult();
                respawned.customName(LegacyComponentSerializer.legacyAmpersand().deserialize("&d" + player.getName() + "'s Pig"));
                respawned.setCustomNameVisible(true);
                ScaleUtil.setScale(respawned, plugin.getConfig().getDouble("pig-scale", 0.45));
                respawned.setLeashHolder(player);
                racer.setPig(respawned);
                plugin.getMessageService().send(player, "pig-lost");
                continue;
            }
            // Intentionally allow outsiders to interfere — we only re-leash if the leash broke.
            if (!pig.isLeashed() || pig.getLeashHolder() == null || !pig.getLeashHolder().getUniqueId().equals(player.getUniqueId())) {
                pig.setLeashHolder(player);
            }
            if (pig.getLocation().distanceSquared(player.getLocation()) > maxDist * maxDist) {
                Location pull = player.getLocation().add(player.getLocation().getDirection().normalize().multiply(1.5));
                pig.teleport(pull);
                pig.setLeashHolder(player);
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
            if (!done) {
                continue;
            }
            markFinished(session, racer, player);
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
            byArena.remove(session.getArenaName());
            return;
        }

        if (session.getPhase() == RaceSession.Phase.RACING
                && session.getPlacements().size() >= session.size()) {
            endRace(session, "complete");
        }
    }

    private void cleanupRacer(RaceSession.Racer racer, Player player, Location exit) {
        if (racer != null && racer.getPig() != null) {
            Pig pig = racer.getPig();
            if (pig.isValid()) {
                try {
                    pig.setLeashHolder(null);
                } catch (Exception ignored) {
                }
                pig.remove();
            }
        }
        if (player != null) {
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            if (racer != null && racer.getPreviousScale() != null) {
                ScaleUtil.setScale(player, racer.getPreviousScale());
            } else {
                ScaleUtil.setScale(player, 1.0);
            }
            if (plugin.getConfig().getBoolean("teleport-on-end", true) && exit != null) {
                player.teleport(exit);
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
}
