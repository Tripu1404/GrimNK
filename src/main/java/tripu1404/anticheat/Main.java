package tripu1404.anticheat;

import cn.nukkit.plugin.PluginBase;
import tripu1404.anticheat.checks.InventoryMoveCheck;
import tripu1404.anticheat.checks.NoSlowDownCheck;

public class Main extends PluginBase {

    @Override
    public void onEnable() {
        // Registramos las clases separadas como Listeners
        this.getServer().getPluginManager().registerEvents(new InventoryMoveCheck(), this);
        this.getServer().getPluginManager().registerEvents(new NoSlowDownCheck(), this);
        
        this.getLogger().info("AntiCheat activado: Checks cargados correctamente.");
    }
}
