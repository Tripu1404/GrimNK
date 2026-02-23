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
import cn.nukkit.event.player.PlayerToggleFlightEvent;
import cn.nukkit.event.player.PlayerToggleGlideEvent;
import cn.nukkit.math.AxisAlignedBB;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FlightCheck implements Listener {

    private final Map<UUID, Integer> airTicks = new HashMap<>();
    
    // Nuevo: Registro del momento exacto de la última infracción en milisegundos
    private final Map<UUID, Long> lastViolation = new HashMap<>();

    @EventHandler
    public void onToggleGlide(PlayerToggleGlideEvent event) {
        Player player = event.getPlayer();
        if (player.isCreative() || player.isSpectator()) return;

        event.setCancelled(true);
        cancelFly(player);
    }

    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (player.isCreative() || player.isSpectator()) return;

        event.setCancelled(true);
        cancelFly(player);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (player.isCreative() || player.isSpectator() || player.getAdventureSettings().get(AdventureSettings.Type.ALLOW_FLIGHT)) {
            return;
        }

        double dY = event.getTo().getY() - event.getFrom().getY();

        // 1. Verificación matemática de suelo
        boolean trulyOnGround = checkGroundState(player);

        if (trulyOnGround) {
            airTicks.remove(uuid);
        } else {
            int ticksInAir = airTicks.getOrDefault(uuid, 0) + 1;
            airTicks.put(uuid, ticksInAir);

            if (dY >= 0) {
                if (ticksInAir > 15) {
                    event.setCancelled(true);
                    cancelFly(player);
                    return;
                }
            }
        }

        // 2. Fallback de seguridad
        if (player.getAdventureSettings().get(AdventureSettings.Type.FLYING) || player.isGliding()) {
            event.setCancelled(true);
            cancelFly(player);
        }
    }

    // --- BLOQUEO DE ACCIONES DURANTE EL HACK ---

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isViolating(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        // Verificamos si el daño fue causado por una entidad a otra
        if (event instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent damageEvent = (EntityDamageByEntityEvent) event;
            
            // Verificamos si el atacante es un jugador
            if (damageEvent.getDamager() instanceof Player) {
                Player damager = (Player) damageEvent.getDamager();
                
                // Si el atacante está en tiempo de penalización por volar, cancelamos el golpe
                if (isViolating(damager)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    // --- MÉTODOS INTERNOS ---

    /**
     * Aplica los castigos y registra el tiempo de la infracción
     */
    private void cancelFly(Player player) {
        // Guardamos el momento exacto en el que detectamos el hack
        lastViolation.put(player.getUniqueId(), System.currentTimeMillis());
        
        player.getAdventureSettings().set(AdventureSettings.Type.ALLOW_FLIGHT, false);
        player.getAdventureSettings().set(AdventureSettings.Type.FLYING, false);
        player.getAdventureSettings().update();
        player.setGliding(false);
        
        player.setMotion(player.getTemporalVector().setComponents(0.0, -5.0, 0.0));
    }

    /**
     * Verifica si han pasado menos de 1.5 segundos desde la última detección de hack
     */
    private boolean isViolating(Player player) {
        long lastTime = lastViolation.getOrDefault(player.getUniqueId(), 0L);
        return (System.currentTimeMillis() - lastTime) < 1500;
    }

    /**
     * Revisa físicamente si hay bloques sólidos debajo de la caja de colisión.
     */
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
