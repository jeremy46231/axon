package app.jer.axon.service;


import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.*;
import meteordevelopment.meteorclient.systems.modules.movement.Velocity;
import meteordevelopment.meteorclient.systems.modules.player.*;
import meteordevelopment.meteorclient.systems.modules.render.Fullbright;
import meteordevelopment.meteorclient.systems.modules.world.EndermanLook;
import meteordevelopment.meteorclient.utils.entity.SortPriority;

public class MeteorService {
    public static void initialize() {
        // Combat Modules
        KillAura killAura = runModule(KillAura.class);
        setSetting(killAura, "auto-switch", true);
        setSetting(killAura, "swap-back", true);
        setSettingString(killAura, "entities", "monster");
        setSetting(killAura, "priority", SortPriority.LowestDistance);
        runModule(ArrowDodge.class);
        runModule(AutoArmor.class);
        runModule(AutoTotem.class);
        runModule(AutoWeapon.class);
        runModule(EndermanLook.class);

        // Item Modules
        AutoReplenish autoReplenish = runModule(AutoReplenish.class);
        setSetting(autoReplenish, "search-hotbar", true);
        runModule(AutoTool.class);
        runModule(AutoEat.class);

        // Movement Modules
        runModule(AntiHunger.class);
        runModule(Multitask.class);
        runModule(Velocity.class);

        // Render Modules
        runModule(Fullbright.class);

        // Config
        Config config = Config.get();
        config.titleScreenCredits.set(false);
        config.titleScreenSplashes.set(false);
    }

    private static <T extends Module> T runModule(Class<T> moduleClass) {
        T module = Modules.get().get(moduleClass);
        if (!module.isActive()) module.toggle();
        return module;
    }

    @SuppressWarnings("unchecked")
    private static <T> Setting<T> getSetting(Module module, String name) {
        Setting<T> setting = (Setting<T>) module.settings.get(name);
        if (setting == null) throw new NullPointerException("Setting not found: " + name);
        return setting;
    }

    private static <T> void setSetting(Module module, String name, T value) {
        getSetting(module, name).set(value);
    }

    private static void setSettingString(Module module, String name, String value) {
        getSetting(module, name).parse(value);
    }

}


