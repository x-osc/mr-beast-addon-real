package mister.breast.addon;

import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.MeteorStarscript;
import mister.breast.addon.modules.EvenBetterAutoSign;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.meteordev.starscript.value.Value;
import org.slf4j.Logger;

public class MrBeastAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("MrBeastAddon");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Mr Beast Meteor Addon (Real) (Not Clickbait)");

        Modules.get().add(new EvenBetterAutoSign());

        MeteorStarscript.ss.set("datetime", (ss, argCount) -> {
            String pattern = "yyyy-MM-dd";
            if (argCount == 1) pattern = ss.popString("datetime() format must be a string.");
            else if (argCount > 1) ss.error("datetime() takes 0 or 1 arguments, got %d.", argCount);

            try {
                return Value.string(LocalDateTime.now().format(DateTimeFormatter.ofPattern(pattern)));
            } catch (IllegalArgumentException e) {
                ss.error("Invalid date format '%s'.", pattern);
                return Value.null_();
            }
        });
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
