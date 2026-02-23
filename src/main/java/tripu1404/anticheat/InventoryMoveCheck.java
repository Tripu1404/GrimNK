package tripu1404.anticheat.checks;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.inventory.InventoryCloseEvent;
import cn.nukkit.event.inventory.InventoryOpenEvent;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.inventory.InventoryType;
import cn.nukkit.potion.Effect;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class InventoryMoveCheck implements Listener {

    private static class InvData {
        int tick;
        boolean isLocal;

        InvData(int tick, boolean isLocal) {
            this.tick = tick;
            this.isLocal = isLocal;
        }
    }

    private final Map<UUID, InvData> openInventories = new HashMap<>();

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        InventoryType type = event.getInventory().getType();
        boolean isLocal = (type == InventoryType.UI || type == InventoryType.CRAFTING || type == InventoryType.PLAYER);
        openInventories.put(event.getPlayer().getUniqueId(), new InvData(Server.getInstance().getTick(), isLocal));
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
        
        if (!openInventories.containsKey(uuid)) {
            return;
        }

        InvData data = openInventories.get(uuid);
        int currentTick = Server.getInstance().getTick();

        if (currentTick - data.tick > 5) {
            double dX = event.getTo().getX() - event.getFrom().getX();
            double dY = event.getTo().getY() - event.getFrom().getY();
            double dZ = event.getTo().getZ() - event.getFrom().getZ();

            boolean isViolating = false;

            // 1. COMPROBACIÓN HORIZONTAL
            double horizontalDistance = (dX * dX) + (dZ * dZ);
            if (horizontalDistance > 0.0001) {
                isViolating = true;
            }

            // 2. COMPROBACIÓN VERTICAL (Solo Máximos)
            if (!isViolating) {
                if (dY > 0) { 
                    // MOVIMIENTO HACIA ARRIBA
                    if (player.hasEffect(Effect.LEVITATION)) {
                        if (dY > 0.25) { // Sin mínimo
                            isViolating = true;
                        }
                    } else {
                        if (dY > 0.5) { // Sin mínimo
                            isViolating = true;
                        }
                    }
                } else if (dY < 0) { 
                    // MOVIMIENTO HACIA ABAJO (Caída)
                    double fallSpeed = Math.abs(dY); 
                    if (player.hasEffect(Effect.SLOW_FALLING)) {
                        if (fallSpeed > 0.55) { // Sin mínimo
                            isViolating = true;
                        }
                    } else {
                        if (fallSpeed > 2.0) { // Sin mínimo
                            isViolating = true;
                        }
                    }
                }
            }

            // Castigo
            if (isViolating) {
                event.setCancelled(true);
                
                if (!data.isLocal) {
                    player.removeAllWindows();
                    openInventories.remove(uuid);
                }
            }
        }
    }
}
