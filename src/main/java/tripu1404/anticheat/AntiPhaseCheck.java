package tripu1404.anticheat.checks;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.level.Location;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AntiPhaseCheck implements Listener {

    private final Map<UUID, Location> lastSafePos = new HashMap<>();

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastSafePos.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        if (player.isCreative() || player.isSpectator()) {
            return;
        }

        // 1. Verificar colisión en el destino
        if (isInsideSolidBlock(player, event.getTo())) {
            event.setCancelled(true);
            resolveViolation(player);
            return;
        }

        // 2. Guardar posición segura (basado en CheatPlayer.kt)
        if (player.isOnGround()) {
            lastSafePos.put(uuid, event.getFrom().clone());
        }
    }

    private boolean isInsideSolidBlock(Player player, Location loc) {
        double radius = (player.getWidth() * 0.8) / 2.0; 
        
        // CORRECCIÓN: Usamos SimpleAxisAlignedBB en lugar de AxisAlignedBB
        AxisAlignedBB bb = new SimpleAxisAlignedBB(
                loc.getX() - radius,
                loc.getY() + 0.2, 
                loc.getZ() - radius,
                loc.getX() + radius,
                loc.getY() + player.getHeight() - 0.2,
                loc.getZ() + radius
        );

        // Lógica de colisión similar a CheatPlayer.kt
        Block[] blocks = player.getLevel().getCollisionBlocks(bb);
        
        for (Block block : blocks) {
            if (block.isSolid() && !block.canPassThrough()) {
                if (block.getBoundingBox() != null && block.getBoundingBox().intersectsWith(bb)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void resolveViolation(Player player) {
        Location back = lastSafePos.get(player.getUniqueId());
        
        if (back != null) {
            // Rubberband en el siguiente tick para forzar posición
            Server.getInstance().getScheduler().scheduleDelayedTask(() -> {
                if (player.isOnline()) {
                    player.teleport(back);
                    player.setMotion(new Vector3(0, 0, 0));
                }
            }, 1);
        }
    }
}
