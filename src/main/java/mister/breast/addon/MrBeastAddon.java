package mister.breast.addon;

import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import mister.breast.addon.modules.ModuleExample;

import org.slf4j.Logger;

public class MrBeastAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("MrBeastAddon");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Mr Beast Meteor Addon (Real) (Not Clickbait)");

        Modules.get().add(new ModuleExample());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "mister.breast";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("x-osc", "mr-beast-addon-real");
    }
}
