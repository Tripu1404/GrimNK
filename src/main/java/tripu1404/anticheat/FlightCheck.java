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

        if (trulyOnGround) {
            airTicks.put(uuid, 0);
            // Guardamos la posición exacta antes de que empiece cualquier movimiento de hack
            lastGroundLoc.put(uuid, player.getLocation().clone());
        } else {
            int ticks = airTicks.getOrDefault(uuid, 0) + 1;
            airTicks.put(uuid, ticks);

            double dY = event.getTo().getY() - event.getFrom().getY();

            // Detección inmediata: si sube o levita sin estar en el suelo
            if (dY >= 0 && ticks > 5) { // 5 ticks es el margen para un salto normal
                event.setCancelled(true);
                processViolation(player);
                return;
            }
        }

        // Si el cliente reporta que está volando internamente
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
        // Quitamos permisos de inmediato
        player.getAdventureSettings().set(AdventureSettings.Type.ALLOW_FLIGHT, false);
        player.getAdventureSettings().set(AdventureSettings.Type.FLYING, false);
        player.getAdventureSettings().update();
        player.setGliding(false);

        Location backPos = lastGroundLoc.get(player.getUniqueId());
        if (backPos != null) {
            // El truco para el Rubberband efectivo en Nukkit:
            // Teletransportar en el siguiente tick para sobrescribir el paquete del cliente.
            Server.getInstance().getScheduler().scheduleDelayedTask(() -> {
                if (player.isOnline()) {
                    player.teleport(backPos);
                    player.setMotion(new Vector3(0, -1, 0)); // Fuerza de caída leve para asegurar contacto
                }
            }, 1);
        }
    }

    private boolean isCurrentlyHacking(Player player) {
        if (player.isCreative() || player.isSpectator()) return false;
        return player.getAdventureSettings().get(AdventureSettings.Type.FLYING) 
                || player.isGliding() 
                || airTicks.getOrDefault(player.getUniqueId(), 0) > 5;
    }

    private boolean checkGroundState(Player player) {
        AxisAlignedBB bb = player.getBoundingBox().clone();
        bb.setMinY(bb.getMinY() - 0.1); // Reducimos el margen para ser más estrictos
        bb.setMaxY(bb.getMinY() + 0.05);

        for (Block block : player.getLevel().getCollisionBlocks(bb)) {
            if (!block.canPassThrough()) return true;
        }
        return false;
    }
}
