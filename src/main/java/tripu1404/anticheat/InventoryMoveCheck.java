package tripu1404.anticheat.checks;

import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.inventory.InventoryCloseEvent;
import cn.nukkit.event.inventory.InventoryOpenEvent;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.potion.Effect;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class InventoryMoveCheck implements Listener {

    private final Set<UUID> openInventories = new HashSet<>();

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        openInventories.add(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        openInventories.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        openInventories.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        if (!openInventories.contains(uuid)) {
            return;
        }

        double dX = event.getTo().getX() - event.getFrom().getX();
        double dY = event.getTo().getY() - event.getFrom().getY();
        double dZ = event.getTo().getZ() - event.getFrom().getZ();

        // 1. COMPROBACIÓN HORIZONTAL (Inventory Move)
        double horizontalDistance = (dX * dX) + (dZ * dZ);
        if (horizontalDistance > 0.0001) {
            event.setCancelled(true);
            
            // FIX DEL DESYNC: Forzamos el cierre del inventario en el servidor 
            // y sacamos al jugador de la lista para que no se quede congelado.
            player.removeAllWindows();
            openInventories.remove(uuid);
            return; 
        }

        // 2. COMPROBACIÓN VERTICAL ESTRICTA
        if (dY > 0) { 
            // MOVIMIENTO HACIA ARRIBA
            if (player.hasEffect(Effect.LEVITATION)) {
                // Levitación: Máximo 0.2, Mínimo 0.15
                if (dY > 0.2 || dY < 0.15) {
                    event.setCancelled(true);
                    player.removeAllWindows();
                    openInventories.remove(uuid);
                }
            } else {
                // Salto normal sin efectos
                if (dY > 0.5) {
                    event.setCancelled(true);
                    player.removeAllWindows();
                    openInventories.remove(uuid);
                }
            }
            
        } else if (dY < 0) { 
            // MOVIMIENTO HACIA ABAJO (Caída)
            double fallSpeed = Math.abs(dY); 

            if (player.hasEffect(Effect.SLOW_FALLING)) {
                // Caída Lenta: Máximo 0.55, Mínimo 0.45
                if (fallSpeed > 0.55 || fallSpeed < 0.45) {
                    event.setCancelled(true);
                    player.removeAllWindows();
                    openInventories.remove(uuid);
                }
            } else {
                // Caída Normal: Solo Máximo 1.10 (Sin mínimo)
                if (fallSpeed > 1.10) {
                    event.setCancelled(true);
                    player.removeAllWindows();
                    openInventories.remove(uuid);
                }
            }
        }
    }
}
