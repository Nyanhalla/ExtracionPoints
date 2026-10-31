package com.nyanhalla.extractionpoints.tasks;

import com.nyanhalla.extractionpoints.ExtractionPoints;
import com.nyanhalla.extractionpoints.config.WorldConfig;
import com.nyanhalla.extractionpoints.managers.PointManager;
import com.nyanhalla.extractionpoints.models.ExtractionPoint;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;

import org.bukkit.HeightMap;
import org.bukkit.World;

import java.util.Random;

/**
 * Tarea de partículas. Renderiza efectos visuales por estado:
 *   IDLE       → rune circle + floor disc + floating sparkles
 *   EXTRACTING → double helix escalado + progress arc con gradiente de color + convergencia + floor glow
 *   COOLDOWN   → humo + anillo agotado + ceniza cayendo
 */
public class ParticleTask extends BukkitRunnable {

    private final ExtractionPoints plugin;
    private int tickCounter = 0;
    private final Random random = new Random();

    public ParticleTask(ExtractionPoints plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        tickCounter++;
        PointManager pointManager = plugin.getPointManager();

        for (ExtractionPoint point : pointManager.getActivePoints()) {
            if (!point.isActive()) continue;

            Location loc = point.getLocation();
            if (loc.getWorld() == null) continue;

            String worldName = loc.getWorld().getName();
            WorldConfig wcfg = plugin.getConfigManager().getWorldConfig(worldName);
            if (!wcfg.isEnabled()) continue;

            // Render distance gate
            int renderDistance = wcfg.getParticleRenderDistance();
            double renderDistSq = (double) renderDistance * renderDistance;
            boolean hasNearbyPlayer = false;
            for (Player player : loc.getWorld().getPlayers()) {
                if (player.getLocation().distanceSquared(loc) <= renderDistSq) {
                    hasNearbyPlayer = true;
                    break;
                }
            }
            if (!hasNearbyPlayer) continue;

            ExtractionPoint.State state = point.getState();
            double pointRadius = wcfg.getPointRadius();

            switch (state) {
                case IDLE -> renderIdle(loc, wcfg, pointRadius, point);
                case EXTRACTING -> renderExtracting(loc, wcfg, pointRadius, point, pointManager);
                case COOLDOWN -> renderCooldown(loc, wcfg, pointRadius);
            }

            if (state != ExtractionPoint.State.COOLDOWN) {
                playAmbientSound(loc, wcfg, pointRadius);
            }
        }
    }

    // ──────────────────────────── IDLE ────────────────────────────

    private void renderIdle(Location loc, WorldConfig wcfg, double pointRadius, ExtractionPoint point) {
        double rotation = tickCounter * 0.05;
        // Outer ring floats 1.2 blocks above the beacon (well above any structure)
        double ringY = 1.2;

        // Particle beam if no real beacon
        if (!point.isBeaconPlaced()) {
            Particle beamParticle = particle(wcfg.getBeamParticle(), Particle.END_ROD);
            int beamHeight = wcfg.getBeamHeight();
            int beamSpacing = Math.max(1, wcfg.getBeamSpacing());
            int beamCount = wcfg.getBeamParticleCount();
            for (int y = 0; y <= beamHeight; y += beamSpacing) {
                Location beamLoc = loc.clone().add(0, y, 0);
                loc.getWorld().spawnParticle(beamParticle, beamLoc, beamCount, 0.0, 0.0, 0.0, 0.0);
            }
        }

        // Outer circle (pulsing + rotating) — floats above structure
        double pulseRadius = pointRadius * (1.0 + 0.10 * Math.sin(tickCounter * 0.1));
        Particle circleParticle = particle(wcfg.getCircleParticle(), Particle.FLAME);
        int circleDensity = wcfg.getCircleDensity();
        int circleCount = wcfg.getCircleParticleCount();
        drawCircle(loc, pulseRadius, circleDensity, rotation, circleParticle, circleCount, ringY);

        // Rune circle: counter-rotating inner ring + cardinal cross
        if (wcfg.isEffectRunes()) {
            Particle runeParticle = particle(wcfg.getEffectRuneParticle(), Particle.SOUL_FIRE_FLAME);
            double innerR = pointRadius * 0.6;
            drawCircle(loc, innerR, Math.max(8, circleDensity / 2), -rotation * 1.5, runeParticle, 1, ringY + 0.15);
            drawCardinalCross(loc, pointRadius * 0.85, rotation * 0.5, runeParticle, ringY);
        }

        // Floor disc: soft cyan DUST — sigue el terreno real
        if (tickCounter % 2 == 0) {
            Particle.DustOptions cyanDust = new Particle.DustOptions(Color.fromRGB(0, 200, 220), 0.9f);
            drawFloorDisc(loc, pointRadius, cyanDust, 14);
        }

        // Floating sparkles: ENCHANT particles drifting upward
        if (tickCounter % 3 == 0) {
            for (int i = 0; i < 4; i++) {
                double angle = random.nextDouble() * 2 * Math.PI;
                double r = random.nextDouble() * pointRadius * 0.9;
                double yOff = ringY + random.nextDouble() * 1.5;
                Location sparkLoc = new Location(loc.getWorld(),
                        loc.getX() + r * Math.cos(angle),
                        loc.getY() + yOff,
                        loc.getZ() + r * Math.sin(angle));
                loc.getWorld().spawnParticle(Particle.ENCHANT, sparkLoc, 1, 0.15, 0.25, 0.15, 0.015);
            }
        }
    }

    // ──────────────────────────── EXTRACTING ────────────────────────────

    private void renderExtracting(Location loc, WorldConfig wcfg, double pointRadius,
                                   ExtractionPoint point, PointManager pointManager) {
        double extractionProgress = point.getExtractionProgress(wcfg.getExtractionTime());
        double ringY = 1.2;

        // Circle rotation speeds up as extraction progresses
        double speedBoost = 1.0 + extractionProgress * 0.8;
        double rotation = tickCounter * 0.10 * speedBoost;

        double pulseRadius = pointRadius * (1.0 + 0.15 * Math.sin(tickCounter * 0.2));
        Particle circleParticle = particle(wcfg.getCircleParticle(), Particle.FLAME);
        drawCircle(loc, pulseRadius, wcfg.getCircleDensity(), rotation, circleParticle,
                wcfg.getCircleParticleCount(), ringY);

        // Beam if no beacon
        if (!point.isBeaconPlaced()) {
            Particle beamParticle = particle(wcfg.getBeamParticle(), Particle.END_ROD);
            int beamHeight = wcfg.getBeamHeight();
            int beamSpacing = Math.max(1, wcfg.getBeamSpacing());
            for (int y = 0; y <= beamHeight; y += beamSpacing) {
                loc.getWorld().spawnParticle(beamParticle, loc.clone().add(0, y, 0),
                        wcfg.getBeamParticleCount(), 0.0, 0.0, 0.0, 0.0);
            }
        }

        // Double helix: grows taller and spins faster as extraction progresses
        if (wcfg.isEffectDoubleHelix()) {
            Particle beamParticle = particle(wcfg.getBeamParticle(), Particle.END_ROD);
            drawDoubleHelixScaled(loc, beamParticle, tickCounter, extractionProgress);
        }

        // Convergence beams pointing to center (slightly above structure)
        if (wcfg.isEffectCharging()) {
            Particle charging = particle(wcfg.getEffectChargingParticle(), Particle.ENCHANT);
            drawConvergence(loc, pointRadius, charging, tickCounter, ringY);
        }

        // Progress arc with dynamic color (green → yellow → red)
        if (wcfg.isEffectProgressRing()) {
            Color ringColor = progressColor(extractionProgress);
            drawProgressArc(loc, pointRadius * 1.15, extractionProgress, ringColor, ringY + 0.1);
        }

        // Floor disc: green glow beneath the extraction area
        if (tickCounter % 2 == 0) {
            Color floorColor = progressColor(extractionProgress);
            drawFloorDisc(loc, pointRadius, new Particle.DustOptions(floorColor, 1.1f), 18);
        }
    }

    // ──────────────────────────── COOLDOWN ────────────────────────────

    private void renderCooldown(Location loc, WorldConfig wcfg, double pointRadius) {
        Particle smoke = particle(wcfg.getEffectCooldownParticle(), Particle.SMOKE);
        double ringY = 1.2;

        // Orbiting smoke puffs above structure
        if (tickCounter % 4 == 0) {
            int pts = 8;
            for (int i = 0; i < pts; i++) {
                double angle = (2 * Math.PI / pts) * i + (tickCounter * 0.01);
                double x = loc.getX() + pointRadius * 0.7 * Math.cos(angle);
                double z = loc.getZ() + pointRadius * 0.7 * Math.sin(angle);
                Location p = new Location(loc.getWorld(), x, loc.getY() + ringY, z);
                loc.getWorld().spawnParticle(smoke, p, 1, 0.0, 0.05, 0.0, 0.01);
            }
        }

        // Depleted ring: dark blue-gray DUST on the ground
        Particle.DustOptions grayDust = new Particle.DustOptions(Color.fromRGB(60, 60, 90), 1.1f);
        drawFloorDisc(loc, pointRadius, grayDust, 12);

        // Falling ash particles
        if (tickCounter % 4 == 0) {
            for (int i = 0; i < 4; i++) {
                double angle = random.nextDouble() * 2 * Math.PI;
                double r = random.nextDouble() * pointRadius * 0.8;
                Location ash = new Location(loc.getWorld(),
                        loc.getX() + r * Math.cos(angle),
                        loc.getY() + 1.5 + random.nextDouble() * 2.0,
                        loc.getZ() + r * Math.sin(angle));
                loc.getWorld().spawnParticle(Particle.ASH, ash, 1, 0.1, 0.0, 0.1, 0.01);
            }
        }
    }

    // ──────────────────────────── Drawing primitives ────────────────────────────

    private void drawCircle(Location center, double radius, int density, double rotation,
                             Particle particle, int count, double yOffset) {
        for (int i = 0; i < density; i++) {
            double angle = (2 * Math.PI / density) * i + rotation;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            Location p = new Location(center.getWorld(), x, center.getY() + yOffset, z);
            center.getWorld().spawnParticle(particle, p, count, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private void drawCardinalCross(Location center, double half, double rotation, Particle particle, double yOffset) {
        int arms = 4;
        int pointsPerArm = 6;
        for (int a = 0; a < arms; a++) {
            double armAngle = (Math.PI / 2) * a + rotation;
            double dirX = Math.cos(armAngle);
            double dirZ = Math.sin(armAngle);
            for (int i = 1; i <= pointsPerArm; i++) {
                double t = (double) i / pointsPerArm;
                double x = center.getX() + dirX * half * t;
                double z = center.getZ() + dirZ * half * t;
                Location p = new Location(center.getWorld(), x, center.getY() + yOffset, z);
                center.getWorld().spawnParticle(particle, p, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    /**
     * Double helix that grows taller and faster as extraction progresses.
     * progress: 0.0 = just started, 1.0 = complete.
     */
    private void drawDoubleHelixScaled(Location center, Particle particle, int tick, double progress) {
        double helixRadius = 1.4 + progress * 0.5;
        double height = 8.0 + progress * 16.0;  // grows 8 → 24 blocks
        int points = 12 + (int)(progress * 10);  // denser helix as progress increases
        double speedMult = 1.0 + progress * 2.5;
        double rot = tick * 0.20 * speedMult;
        for (int i = 0; i < points; i++) {
            double t = (double) i / points;
            double angleA = t * 2 * Math.PI * 2 + rot;
            double angleB = -t * 2 * Math.PI * 2 + rot;
            double y = t * height;
            spawnHelixPoint(center, helixRadius, angleA, y, particle);
            spawnHelixPoint(center, helixRadius, angleB, y, particle);
        }
    }

    private void spawnHelixPoint(Location center, double radius, double angle, double y, Particle particle) {
        double x = center.getX() + radius * Math.cos(angle);
        double z = center.getZ() + radius * Math.sin(angle);
        Location p = new Location(center.getWorld(), x, center.getY() + y, z);
        center.getWorld().spawnParticle(particle, p, 1, 0.0, 0.0, 0.0, 0.0);
    }

    private void drawConvergence(Location center, double radius, Particle particle, int tick, double yOffset) {
        int beams = 8;
        double rot = tick * 0.05;
        for (int i = 0; i < beams; i++) {
            double angle = (2 * Math.PI / beams) * i + rot;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            double vx = -Math.cos(angle) * 0.4;
            double vz = -Math.sin(angle) * 0.4;
            Location p = new Location(center.getWorld(), x, center.getY() + yOffset, z);
            center.getWorld().spawnParticle(particle, p, 0, vx, 0.0, vz, 0.4);
        }
    }

    /**
     * Fills an arc around the center proportional to the extraction fraction.
     * Color transitions from green to red with progress.
     */
    private void drawProgressArc(Location center, double radius, double fraction, Color color, double yOffset) {
        if (fraction <= 0) return;
        int totalPoints = 60;
        int filled = (int) Math.round(totalPoints * Math.min(1.0, fraction));
        double startOffset = -Math.PI / 2; // top (north)
        Particle.DustOptions dustOpts = new Particle.DustOptions(color, 1.5f);
        for (int i = 0; i < filled; i++) {
            double angle = startOffset + (2 * Math.PI / totalPoints) * i;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            Location p = new Location(center.getWorld(), x, center.getY() + yOffset, z);
            center.getWorld().spawnParticle(Particle.DUST, p, 1, dustOpts);
        }
    }

    /**
     * Renders a floor disc of DUST particles for ambient ground glow.
     */
    private void drawFloorDisc(Location center, double radius, Particle.DustOptions opts, int density) {
        for (int i = 0; i < density; i++) {
            double angle = (2 * Math.PI / density) * i + (tickCounter * 0.04);
            // Vary radius across three concentric "shells"
            double r = radius * (0.3 + 0.35 * (i % 3));
            double x = center.getX() + r * Math.cos(angle);
            double z = center.getZ() + r * Math.sin(angle);
            Location p = new Location(center.getWorld(), x, center.getY() + 0.07, z);
            center.getWorld().spawnParticle(Particle.DUST, p, 1, opts);
        }
    }

    // ──────────────────────────── Helpers ────────────────────────────

    /**
     * Color gradient based on extraction progress.
     * 0.0 = just started (green), 1.0 = complete (red).
     */
    private Color progressColor(double progress) {
        progress = Math.max(0.0, Math.min(1.0, progress));
        if (progress < 0.5) {
            int r = (int)(progress * 2.0 * 255);
            return Color.fromRGB(r, 255, 0);
        } else {
            int g = (int)((1.0 - (progress - 0.5) * 2.0) * 255);
            return Color.fromRGB(255, Math.max(0, g), 0);
        }
    }

    private void playAmbientSound(Location loc, WorldConfig wcfg, double pointRadius) {
        String soundName = wcfg.getAmbientSound();
        if (soundName == null || soundName.isEmpty()) return;
        int interval = wcfg.getAmbientSoundInterval();
        if (interval <= 0 || tickCounter % interval != 0) return;

        Sound sound;
        try {
            sound = Sound.valueOf(soundName);
        } catch (IllegalArgumentException e) {
            return;
        }
        float volume = wcfg.getAmbientSoundVolume();
        float pitch = wcfg.getAmbientSoundPitch();
        double hearingRange = wcfg.getParticleRenderDistance() / 2.0;
        double radiusSq = hearingRange * hearingRange;

        for (Player player : loc.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(loc) <= radiusSq) {
                player.playSound(loc, sound, volume, pitch);
            }
        }
    }

    private Particle particle(String name, Particle fallback) {
        if (name == null || name.isEmpty()) return fallback;
        try {
            return Particle.valueOf(name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    // ──────────────────────────── Completion effect ────────────────────────────

    /**
     * Epic multi-stage completion effect: lightning, totem burst, expanding rings,
     * staggered fireworks, and a rising pillar.
     */
    public static void spawnCompletionEffect(Location loc, WorldConfig wcfg) {
        if (loc.getWorld() == null) return;

        // Cosmetic lightning strike at center (no damage, no fire)
        loc.getWorld().strikeLightningEffect(loc);

        // Central totem particle burst
        loc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 1, 0),
                150, 2.0, 3.0, 2.0, 0.20);

        // End-rod pillar rising upward
        for (int y = 0; y < 28; y++) {
            loc.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0, y, 0),
                    4, 0.5, 0.0, 0.5, 0.02);
        }

        // Sounds
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 1.6f);
        loc.getWorld().playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

        org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin("ExtractionPoints");
        if (plugin == null) return;

        // Expanding shockwave rings (5 rings, timed 2 ticks apart)
        for (int ring = 1; ring <= 5; ring++) {
            final double r = ring * 2.8;
            final int ringDensity = 28 + ring * 6;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (loc.getWorld() == null) return;
                for (int i = 0; i < ringDensity; i++) {
                    double angle = (2 * Math.PI / ringDensity) * i;
                    double x = loc.getX() + r * Math.cos(angle);
                    double z = loc.getZ() + r * Math.sin(angle);
                    Location p = new Location(loc.getWorld(), x, loc.getY() + 0.4, z);
                    loc.getWorld().spawnParticle(Particle.FIREWORK, p, 3, 0.1, 0.3, 0.1, 0.05);
                    loc.getWorld().spawnParticle(Particle.END_ROD, p, 1, 0.0, 0.25, 0.0, 0.04);
                }
            }, ring * 2L);
        }

        // Staggered multi-color fireworks at different heights and offsets
        Color[] fwColors  = {Color.AQUA,   Color.YELLOW, Color.WHITE,  Color.LIME};
        Color[] fwFades   = {Color.BLUE,   Color.ORANGE, Color.SILVER, Color.GREEN};
        FireworkEffect.Type[] fwTypes = {
            FireworkEffect.Type.BURST, FireworkEffect.Type.STAR,
            FireworkEffect.Type.BALL_LARGE, FireworkEffect.Type.BURST
        };
        double[][] offsets = {{0, 0}, {1.5, 1.5}, {-1.5, 1.5}, {0, -1.5}};

        for (int fw = 0; fw < 4; fw++) {
            final int idx = fw;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (loc.getWorld() == null) return;
                Location fwLoc = loc.clone().add(offsets[idx][0], idx * 1.5 + 1.0, offsets[idx][1]);
                try {
                    Firework fw2 = (Firework) loc.getWorld().spawnEntity(
                            fwLoc, org.bukkit.entity.EntityType.FIREWORK_ROCKET);
                    FireworkMeta meta = fw2.getFireworkMeta();
                    meta.setPower(1);
                    meta.addEffect(FireworkEffect.builder()
                            .with(fwTypes[idx])
                            .withColor(fwColors[idx])
                            .withFade(fwFades[idx])
                            .withFlicker()
                            .withTrail()
                            .build());
                    fw2.setFireworkMeta(meta);
                    fw2.setSilent(true);
                    Bukkit.getScheduler().runTaskLater(plugin,
                            () -> { if (!fw2.isDead()) fw2.detonate(); }, 3L);
                } catch (Throwable ignored) {}
            }, idx * 4L);
        }

        // Delayed second sound (ding of victory)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (loc.getWorld() != null) {
                loc.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.2f);
            }
        }, 10L);
    }
}
