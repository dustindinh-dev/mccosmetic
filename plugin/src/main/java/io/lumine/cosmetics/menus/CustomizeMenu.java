package io.lumine.cosmetics.menus;

import com.google.common.collect.Lists;
import io.lumine.cosmetics.MCCosmeticsPlugin;
import io.lumine.cosmetics.api.cosmetics.Cosmetic;
import io.lumine.cosmetics.constants.CosmeticType;
import io.lumine.cosmetics.players.Profile;
import io.lumine.utils.config.properties.types.MenuProp;
import io.lumine.utils.logging.Log;
import io.lumine.utils.menu.EditableMenuBuilder;

import java.util.List;

public class CustomizeMenu extends CosmeticMenu<Profile> {

    public CustomizeMenu(MCCosmeticsPlugin core, MenuManager manager) {
        super(core, manager, new MenuProp(core, "menus/customize", "Menu", null));
    }

    @Override
    public EditableMenuBuilder<Profile> build(EditableMenuBuilder<Profile> builder) {

        for(var entry : getPlugin().getCosmetics().getCosmeticManagers().entrySet()) {
            final var manager = entry.getValue();
            final var type = CosmeticType.folder(manager.getCosmeticClass());

            builder.getIcon("BUTTON_" + type.toUpperCase()).ifPresent(icon -> {
                Log.info("Found cosmetic menu button for {0}", type);
                icon.getBuilder().click((profile,player) -> {
                    // TODO: sorting
                    List<Cosmetic> cosmetics = Lists.newArrayList(manager.getAllCosmetics());
                    
                    Log.info("click");
                    
                    if(manager.getMenu() == null) {
                        getMenuManager().getSelectionMenu().open(player, new SelectionMenuContext(profile,manager), cosmetics);
                    } else {
                        manager.getMenu().open(player, profile, cosmetics);
                    }
                });
            });
        }
        
        return builder;
    }

}
