package io.lumine.cosmetics.menus;

import org.bukkit.entity.Player;

import io.lumine.cosmetics.MCCosmeticsPlugin;
import io.lumine.cosmetics.players.Profile;
import io.lumine.utils.config.properties.types.MenuProp;
import io.lumine.utils.menu.EditableMenuBuilder;
import io.lumine.utils.menu.Menu;
import io.lumine.utils.menu.ReloadableMenu;
import lombok.Getter;

public abstract class CosmeticMenu<T> extends ReloadableMenu<T> {

    @Getter protected MCCosmeticsPlugin plugin;
    @Getter protected MenuManager menuManager;
    
    public CosmeticMenu(MCCosmeticsPlugin core, MenuManager manager, MenuProp menu) {
        super(menu);
        this.plugin = core;
        this.menuManager = manager;
    }
    
    public CosmeticMenu(MCCosmeticsPlugin core, MenuManager manager, MenuProp menu, boolean buildOnOpen) {
        super(menu, buildOnOpen);
        this.plugin = core;
        this.menuManager = manager;
    }
    
    public void openMenu(Player player) {
        Profile profile = plugin.getProfiles().getProfile(player);

        //boolean b = profile.getHatIsActive();
        
        //profile.setHatIsActive(false);
        //MainMenu.playMenuClick(player);
        //menu.open(player, state);
        //profile.setHatIsActive(b);
    }
    
    public EditableMenuBuilder<Profile> addPageButtons(EditableMenuBuilder<Profile> builder) {
        builder.getIcon("NEXT_PAGE").ifPresent(icon -> {
            icon.getBuilder().click((profile,player) -> {
                //MainMenu.playMenuClick(player);
                nextPage(player);
            });
        });
        builder.getIcon("PREVIOUS_PAGE").ifPresent(icon -> {
            icon.getBuilder().click((profile,player) -> {
                //MainMenu.playMenuClick(player);
                previousPage(player);
            });
        });
        return builder;
    }
}
