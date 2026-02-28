package tripu1404.anticheat.checks;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.level.Location;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.Vector3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AntiPhaseCheck implements Listener {

    private final Map<UUID, Location> lastSafePos = new HashMap<>();

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        
        if (player.isCreative() || player.isSpectator()) return;

        // 1. Verificamos si la posición de destino está dentro de un bloque sólido
        if (isInsideSolidBlock(player, event.getTo())) {
            event.setCancelled(true);
            resolveViolation(player);
            return;
        }

        // 2. Si la posición es segura y el jugador está en el suelo, la guardamos
        // Usamos la lógica de detección de suelo de CheatPlayer.kt
        if (player.isOnGround()) {
            lastSafePos.put(player.getUniqueId(), event.getFrom().clone());
        }
    }

    /**
     * Comprueba si el jugador colisiona con bloques sólidos en una ubicación específica.
     */
    private boolean isInsideSolidBlock(Player player, Location loc) {
        // Clonamos la caja de colisión del jugador en la posición de destino
        double radius = (player.getWidth() * 0.9) / 2.0; // Reducimos un poco para evitar falsos positivos por bordes
        AxisAlignedBB bb = new AxisAlignedBB(
                loc.getX() - radius,
                loc.getY() + 0.1, // Elevamos un poco para no detectar el suelo como fase
                loc.getZ() - radius,
                loc.getX() + radius,
                loc.getY() + player.getHeight() - 0.1,
                loc.getZ() + radius
        );

        // Obtenemos bloques que colisionan con esta caja
        Block[] blocks = player.getLevel().getCollisionBlocks(bb);
        
        for (Block block : blocks) {
            // Verificamos si el bloque es sólido (Piedra, Obsidiana, etc.)
            // Ignoramos bloques por los que se puede pasar (Agua, Aire, Pasto)
            if (block.isSolid() && !block.canPassThrough()) {
                // Verificamos si realmente el bloque ocupa un espacio entero
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
            // Forzamos el Rubberband en el siguiente tick
            Server.getInstance().getScheduler().scheduleDelayedTask(() -> {
                if (player.isOnline()) {
                    player.teleport(back);
                    player.setMotion(new Vector3(0, 0, 0));
                }
            }, 1);
        }
    }
}
