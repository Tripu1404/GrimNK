package tripu1404.anticheat.checks;

import cn.nukkit.AdventureSettings;
import cn.nukkit.Player;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FlightCheck implements Listener {

    private final Map<UUID, Integer> airTicks = new HashMap<>();
    
    // Nuevo: Guardamos la última posición legal en el suelo de cada jugador
    private final Map<UUID, Location> lastGroundLoc = new HashMap<>();

    // --- LIMPIEZA DE MEMORIA ---
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        airTicks.remove(uuid);
        lastGroundLoc.remove(uuid);
    }

    @EventHandler
    public void onToggleGlide(PlayerToggleGlideEvent event) {
        Player player = event.getPlayer();
        if (player.isCreative() || player.isSpectator()) return;

        event.setCancelled(true);
        cancelFly(player, null);
    }

    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (player.isCreative() || player.isSpectator()) return;

        event.setCancelled(true);
        cancelFly(player, null);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (player.isCreative() || player.isSpectator() || player.getAdventureSettings().get(AdventureSettings.Type.ALLOW_FLIGHT)) {
            return;
        }

        double dY = event.getTo().getY() - event.getFrom().getY();

        boolean trulyOnGround = checkGroundState(player);

        if (trulyOnGround) {
            airTicks.remove(uuid);
            // Actualizamos la posición segura siempre que esté pisando un bloque
            lastGroundLoc.put(uuid, event.getFrom().clone());
        } else {
            int ticksInAir = airTicks.getOrDefault(uuid, 0) + 1;
            airTicks.put(uuid, ticksInAir);

            if (dY >= 0) {
                if (ticksInAir > 15) {
                    cancelFly(player, event);
                    return;
                }
            }
        }

        if (player.getAdventureSettings().get(AdventureSettings.Type.FLYING) || player.isGliding()) {
            cancelFly(player, event);
        }
    }

    // --- BLOQUEO ESTRICTO DE ACCIONES (SIN INTERVALOS) ---

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isViolating(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent damageEvent = (EntityDamageByEntityEvent) event;
            if (damageEvent.getDamager() instanceof Player) {
                Player damager = (Player) damageEvent.getDamager();
                if (isViolating(damager)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    // --- MÉTODOS INTERNOS ---

    private void cancelFly(Player player, PlayerMoveEvent event) {
        player.getAdventureSettings().set(AdventureSettings.Type.ALLOW_FLIGHT, false);
        player.getAdventureSettings().set(AdventureSettings.Type.FLYING, false);
        player.getAdventureSettings().update();
        player.setGliding(false);
        
        // Buscamos la última posición en el suelo de este jugador
        Location safeLoc = lastGroundLoc.get(player.getUniqueId());
        
        if (safeLoc != null) {
            if (event != null) {
                // Si lo detectamos al moverse, cancelamos el movimiento y lo forzamos de vuelta
                event.setCancelled(true);
                event.setTo(safeLoc);
            } else {
                // Si lo detectamos intentando activar el hack por botón, lo teletransportamos
                player.teleport(safeLoc);
            }
        } else {
            // Fallback por si la posición es nula (ej. justo al entrar al servidor)
            if (event != null) event.setCancelled(true);
        }
    }

    private boolean isViolating(Player player) {
        // Chequeo en tiempo real. Si en este preciso instante sus settings son ilegales 
        // o lleva demasiado tiempo elevándose sin tocar un bloque, la acción se cancela.
        if (player.getAdventureSettings().get(AdventureSettings.Type.FLYING) || player.isGliding()) {
            return true;
        }
        return airTicks.getOrDefault(player.getUniqueId(), 0) > 15;
    }

    private boolean checkGroundState(Player player) {
        AxisAlignedBB bb = player.getBoundingBox().clone();
        bb.setMinY(bb.getMinY() - 0.6);
        bb.setMaxY(bb.getMinY() + 0.1);

        Block[] blocksUnder = player.getLevel().getCollisionBlocks(bb);
        for (Block block : blocksUnder) {
            if (!block.canPassThrough()) {
                return true; 
            }
        }
        return false; 
    }
}
