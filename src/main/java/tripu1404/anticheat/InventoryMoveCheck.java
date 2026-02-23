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

    // Clase interna para guardar el tick y saber qué tipo de inventario es
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
        
        // Verificamos si es el inventario propio del jugador (UI, CRAFTING, o PLAYER)
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

            // 2. COMPROBACIÓN VERTICAL
            if (!isViolating) {
                if (dY > 0) { 
                    if (player.hasEffect(Effect.LEVITATION)) {
                        if (dY > 0.2 || dY < 0.15) {
                            isViolating = true;
                        }
                    } else {
                        if (dY > 0.5) {
                            isViolating = true;
                        }
                    }
                } else if (dY < 0) { 
                    double fallSpeed = Math.abs(dY); 
                    if (player.hasEffect(Effect.SLOW_FALLING)) {
                        if (fallSpeed > 0.55 || fallSpeed < 0.45) {
                            isViolating = true;
                        }
                    } else {
                        if (fallSpeed > 1.10) {
                            isViolating = true;
                        }
                    }
                }
            }

            // Castigo
            if (isViolating) {
                event.setCancelled(true);
                
                // Si NO es el inventario local (es decir, es un cofre), aplicamos la limpieza para evitar desync
                if (!data.isLocal) {
                    player.removeAllWindows();
                    openInventories.remove(uuid);
                }
                // Si ES el inventario local, no hacemos nada más. El evento se cancela
                // y en el próximo tick volverá a entrar a esta comprobación hasta que lo cierre.
            }
        }
    }
}
