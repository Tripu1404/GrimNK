package tripu1404.anticheat;

import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;
import tripu1404.anticheat.checks.InventoryMoveCheck;
import tripu1404.anticheat.checks.NoSlowDownCheck;

public class Main extends PluginBase {

    @Override
    public void onEnable() {
        // Genera el archivo config.yml en la carpeta del plugin si no existe
        this.saveDefaultConfig();
        
        // Obtenemos la configuración
        Config config = this.getConfig();

        // Cargamos el check de Inventory Move si está activado
        if (config.getBoolean("checks.inventory-move", true)) {
            this.getServer().getPluginManager().registerEvents(new InventoryMoveCheck(), this);
        }

        // Cargamos el check de NoSlowDown si está activado
        if (config.getBoolean("checks.no-slow-down", true)) {
            this.getServer().getPluginManager().registerEvents(new NoSlowDownCheck(), this);
        }
        
        // Único mensaje que se enviará a la consola al iniciar
        this.getLogger().info("§aAntiCheat activado correctamente.");
    }
}
