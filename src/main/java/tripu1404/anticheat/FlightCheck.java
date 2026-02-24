package tripu1404.anticheat.checks;

import cn.nukkit.AdventureSettings;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.block.BlockPlaceEvent;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.event.player.PlayerToggleFlightEvent;
import cn.nukkit.event.player.PlayerToggleGlideEvent;
import cn.nukkit.level.Location;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.Vector3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FlightCheck implements Listener {

    private final Map<UUID, Integer> airTicks = new HashMap<>();
    private final Map<UUID, Location> lastGroundLoc = new HashMap<>();

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        airTicks.remove(uuid);
        lastGroundLoc.remove(uuid);
    }

    @EventHandler
    public void onToggleGlide(PlayerToggleGlideEvent event) {
        if (event.getPlayer().isCreative() || event.getPlayer().isSpectator()) return;
        event.setCancelled(true);
        processViolation(event.getPlayer());
    }

    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        if (event.getPlayer().isCreative() || event.getPlayer().isSpectator()) return;
        event.setCancelled(true);
        processViolation(event.getPlayer());
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (player.isCreative() || player.isSpectator() || player.getAdventureSettings().get(AdventureSettings.Type.FLYING)) {
            return;
        }

        // Comprobamos si está en agua, lava o escalando (Lógica de CheatPlayer.kt)
        boolean isClimbing = isClimbing(player);
        boolean isInLiquid = player.isInsideOfWater() || isInLava(player);
        boolean trulyOnGround = checkGroundState(player);
        
        double dY = event.getTo().getY() - event.getFrom().getY();

        if (trulyOnGround || isClimbing || isInLiquid) {
            airTicks.put(uuid, 0);
            lastGroundLoc.put(uuid, player.getLocation().clone());
        } else {
            int ticks = airTicks.getOrDefault(uuid, 0) + 1;
            airTicks.put(uuid, ticks);

            Location ground = lastGroundLoc.get(uuid);
            double heightFromGround = (ground != null) ? (event.getTo().getY() - ground.getY()) : 0;

            if (dY > 0) { 
                // Aumentamos el margen a 16 ticks y 1.5 de altura para mayor compatibilidad
                if (ticks > 16 || heightFromGround > 1.5) {
                    event.setCancelled(true);
                    processViolation(player);
                    return;
                }
            } else if (dY == 0) {
                if (ticks > 12) {
                    event.setCancelled(true);
                    processViolation(player);
                    return;
                }
            } else {
                if (Math.abs(dY) > 2.0) {
                    event.setCancelled(true);
                    processViolation(player);
                }
            }
        }

        if (player.getAdventureSettings().get(AdventureSettings.Type.FLYING) || player.isGliding()) {
            event.setCancelled(true);
            processViolation(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isViolating(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent ev = (EntityDamageByEntityEvent) event;
            if (ev.getDamager() instanceof Player && isViolating((Player) ev.getDamager())) {
                event.setCancelled(true);
            }
        }
    }

    private void processViolation(Player player) {
        player.getAdventureSettings().set(AdventureSettings.Type.ALLOW_FLIGHT, false);
        player.getAdventureSettings().set(AdventureSettings.Type.FLYING, false);
        player.getAdventureSettings().update();
        player.setGliding(false);

        Location backPos = lastGroundLoc.get(player.getUniqueId());
        if (backPos != null) {
            Server.getInstance().getScheduler().scheduleDelayedTask(() -> {
                if (player.isOnline()) {
                    player.teleport(backPos);
                    player.setMotion(new Vector3(0, -1, 0));
                }
            }, 1);
        }
    }

    private boolean isViolating(Player player) {
        if (player.isCreative() || player.isSpectator()) return false;
        int ticks = airTicks.getOrDefault(player.getUniqueId(), 0);
        return (ticks > 16 || player.isGliding() || player.getAdventureSettings().get(AdventureSettings.Type.FLYING));
    }

    private boolean checkGroundState(Player player) {
        AxisAlignedBB bb = player.getBoundingBox().clone();
        bb.setMinY(bb.getMinY() - 0.5); 
        bb.setMaxY(bb.getMinY() + 0.1);
        for (Block block : player.getLevel().getCollisionBlocks(bb)) {
            if (!block.canPassThrough()) return true;
        }
        return false;
    }

    private boolean isInLava(Player player) {
        return player.getLevel().getBlock(player).getId() == BlockID.LAVA || 
               player.getLevel().getBlock(player).getId() == BlockID.STILL_LAVA;
    }

    private boolean isClimbing(Player player) {
        // Verifica si el bloque actual es escalable (Escaleras, Vines, Lianas)
        Block block = player.getLevel().getBlock(player);
        int id = block.getId();
        return id == BlockID.LADDER || id == BlockID.VINE || 
               id == BlockID.WEEPING_VINES || id == BlockID.TWISTING_VINES ||
               id == BlockID.SCAFFOLDING;
    }
}
