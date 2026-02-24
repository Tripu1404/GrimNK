package tripu1404.anticheat.checks;

import cn.nukkit.AdventureSettings;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.block.BlockPlaceEvent;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.event.player.PlayerToggleFlightEvent;
import cn.nukkit.event.player.PlayerToggleGlideEvent;
import cn.nukkit.item.Item;
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

    // Permitir el planeo legítimo si el plugin lo autoriza
    @EventHandler
    public void onToggleGlide(PlayerToggleGlideEvent event) {
        Player player = event.getPlayer();
        if (player.isCreative() || player.isSpectator()) return;
        
        // Si tienes las Elytras equipadas, permitimos el cambio de estado
        if (player.getInventory().getChestplate().getId() == Item.ELYTRA) {
            return;
        }
        
        event.setCancelled(true);
        processViolation(player);
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

        // --- EXCEPCIONES DE MOVIMIENTO LEGÍTIMO ---
        boolean isClimbing = isClimbing(player);
        boolean isInLiquid = player.isInsideOfWater() || isInLava(player);
        boolean isOnSlime = isOnSlime(player);
        boolean trulyOnGround = checkGroundState(player);
        boolean isRiptiding = player.getDataFlag(Entity.DATA_FLAGS, 30); // Acción de Riptide (ID 30)
        boolean isGliding = player.isGliding() && player.getInventory().getChestplate().getId() == Item.ELYTRA;

        double dY = event.getTo().getY() - event.getFrom().getY();

        // Si el jugador está en un estado que permite estar en el aire, reseteamos contadores
        if (trulyOnGround || isClimbing || isInLiquid || isOnSlime || isRiptiding || isGliding) {
            airTicks.put(uuid, 0);
            lastGroundLoc.put(uuid, player.getLocation().clone());
        } else {
            int ticks = airTicks.getOrDefault(uuid, 0) + 1;
            airTicks.put(uuid, ticks);

            Location ground = lastGroundLoc.get(uuid);
            double heightFromGround = (ground != null) ? (event.getTo().getY() - ground.getY()) : 0;

            if (dY > 0) { 
                // Límites de altura: 1.6 para salto normal, 7.0 para impulsos (Slime)
                double maxHeight = isOnSlime ? 7.0 : 1.6;
                if (ticks > 16 || heightFromGround > maxHeight) {
                    event.setCancelled(true);
                    processViolation(player);
                    return;
                }
            } else if (dY == 0) {
                if (ticks > 12) {
                    event.setCancelled(true);
                    processViolation(player);
                    return;
                }
            } else {
                // Caída normal hasta 2.0 de velocidad
                if (Math.abs(dY) > 2.0) {
                    event.setCancelled(true);
                    processViolation(player);
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isViolating(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent ev = (EntityDamageByEntityEvent) event;
            if (ev.getDamager() instanceof Player && isViolating((Player) ev.getDamager())) {
                event.setCancelled(true);
            }
        }
    }

    private void processViolation(Player player) {
        // Solo revertimos settings si el jugador NO tiene Elytras puestas
        if (player.getInventory().getChestplate().getId() != Item.ELYTRA) {
            player.getAdventureSettings().set(AdventureSettings.Type.ALLOW_FLIGHT, false);
            player.getAdventureSettings().set(AdventureSettings.Type.FLYING, false);
            player.getAdventureSettings().update();
            player.setGliding(false);
        }

        Location backPos = lastGroundLoc.get(player.getUniqueId());
        if (backPos != null) {
            Server.getInstance().getScheduler().scheduleDelayedTask(() -> {
                if (player.isOnline()) {
                    player.teleport(backPos);
                    player.setMotion(new Vector3(0, -1, 0));
                }
            }, 1);
        }
    }

    private boolean isViolating(Player player) {
        if (player.isCreative() || player.isSpectator()) return false;
        
        // Un jugador está hackeando si está en el aire sin motivo legítimo (Riptide o Elytra)
        boolean isLegitAir = player.getDataFlag(Entity.DATA_FLAGS, 30) || 
                            (player.isGliding() && player.getInventory().getChestplate().getId() == Item.ELYTRA);
        
        return !isLegitAir && airTicks.getOrDefault(player.getUniqueId(), 0) > 16;
    }

    private boolean checkGroundState(Player player) {
        AxisAlignedBB bb = player.getBoundingBox().clone();
        bb.setMinY(bb.getMinY() - 0.1); 
        bb.setMaxY(bb.getMinY() + 0.05);
        for (Block block : player.getLevel().getCollisionBlocks(bb)) {
            if (!block.canPassThrough()) return true;
        }
        return false;
    }

    private boolean isInLava(Player player) {
        int id = player.getLevel().getBlock(player).getId();
        return id == BlockID.LAVA || id == BlockID.STILL_LAVA;
    }

    private boolean isClimbing(Player player) {
        int id = player.getLevel().getBlock(player).getId();
        return id == BlockID.LADDER || id == BlockID.VINE || 
               id == BlockID.WEEPING_VINES || id == BlockID.TWISTING_VINES ||
               id == BlockID.SCAFFOLDING;
    }

    private boolean isOnSlime(Player player) {
        AxisAlignedBB bb = player.getBoundingBox().clone();
        bb.setMinY(bb.getMinY() - 0.7);
        for (Block block : player.getLevel().getCollisionBlocks(bb)) {
            if (block.getId() == BlockID.SLIME_BLOCK) return true;
        }
        return false;
    }
}
