package tripu1404.anticheat.checks;

import cn.nukkit.AdventureSettings;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
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

        if (player.isCreative() || player.isSpectator() || player.getAdventureSettings().get(AdventureSettings.Type.ALLOW_FLIGHT)) {
            return;
        }

        boolean trulyOnGround = checkGroundState(player);
        double dY = event.getTo().getY() - event.getFrom().getY();

        if (trulyOnGround) {
            airTicks.put(uuid, 0);
            lastGroundLoc.put(uuid, player.getLocation().clone());
        } else {
            int ticks = airTicks.getOrDefault(uuid, 0) + 1;
            airTicks.put(uuid, ticks);

            // --- LÓGICA DE DETECCIÓN MEJORADA ---
            
            // 1. Si está subiendo o flotando (dY >= 0)
            if (dY >= 0) {
                // Si lleva más de 5 ticks subiendo/flotando sin suelo, es Fly
                if (ticks > 5) {
                    event.setCancelled(true);
                    processViolation(player);
                    return;
                }
            } else {
                // 2. Si está bajando (dY < 0), es una caída.
                // Solo cancelamos si la velocidad de caída es humanamente imposible (> 2.0)
                if (Math.abs(dY) > 2.0) {
                    event.setCancelled(true);
                    processViolation(player);
                    return;
                }
                // Importante: No cancelamos el movimiento en caídas normales aunque ticks > 5
            }
        }

        if (player.getAdventureSettings().get(AdventureSettings.Type.FLYING) || player.isGliding()) {
            event.setCancelled(true);
            processViolation(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isCurrentlyHacking(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent ev = (EntityDamageByEntityEvent) event;
            if (ev.getDamager() instanceof Player && isCurrentlyHacking((Player) ev.getDamager())) {
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
                    player.setMotion(new Vector3(0, -0.5, 0)); 
                }
            }, 1);
        }
    }

    private boolean isCurrentlyHacking(Player player) {
        if (player.isCreative() || player.isSpectator()) return false;
        
        // Un jugador está "hackeando" si vuela internamente o si sube/flota sin suelo por mucho tiempo
        boolean internalFly = player.getAdventureSettings().get(AdventureSettings.Type.FLYING) || player.isGliding();
        boolean airJump = airTicks.getOrDefault(player.getUniqueId(), 0) > 5;
        
        // Solo consideramos airJump si el jugador no está cayendo (Y vel < 0)
        // Pero para simplificar en eventos de daño/bloques, si está en el aire > 5 y no tiene suelo:
        return internalFly || airJump;
    }

    private boolean checkGroundState(Player player) {
        AxisAlignedBB bb = player.getBoundingBox().clone();
        // Usamos la lógica de colisión estricta de tu código base
        bb.setMinY(bb.getMinY() - 0.1); 
        bb.setMaxY(bb.getMinY() + 0.05);

        for (Block block : player.getLevel().getCollisionBlocks(bb)) {
            if (!block.canPassThrough()) return true;
        }
        return false;
    }
}
