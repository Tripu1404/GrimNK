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
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.Vector3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FlightCheck implements Listener {

    private final Map<UUID, Integer> airTicks = new HashMap<>();
    private final Map<UUID, Long> lastViolation = new HashMap<>();

    // --- LIMPIEZA DE MEMORIA ---
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        airTicks.remove(uuid);
        lastViolation.remove(uuid);
    }

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

    private void cancelFly(Player player) {
        lastViolation.put(player.getUniqueId(), System.currentTimeMillis());
        
        player.getAdventureSettings().set(AdventureSettings.Type.ALLOW_FLIGHT, false);
        player.getAdventureSettings().set(AdventureSettings.Type.FLYING, false);
        player.getAdventureSettings().update();
        player.setGliding(false);
        
        // CORRECCIÓN: Usamos un nuevo Vector3 en lugar de getTemporalVector()
        player.setMotion(new Vector3(0.0, -5.0, 0.0));
    }

    private boolean isViolating(Player player) {
        long lastTime = lastViolation.getOrDefault(player.getUniqueId(), 0L);
        return (System.currentTimeMillis() - lastTime) < 1500;
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
