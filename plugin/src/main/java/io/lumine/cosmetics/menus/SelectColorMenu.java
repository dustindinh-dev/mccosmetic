package io.lumine.cosmetics.menus;

import io.lumine.cosmetics.MCCosmeticsPlugin;
import io.lumine.cosmetics.players.Profile;
import io.lumine.utils.config.properties.types.MenuProp;
import io.lumine.utils.menu.EditableMenuBuilder;
import io.lumine.utils.menu.Icon;
import io.lumine.utils.menu.MenuData;

public class SelectColorMenu extends CosmeticMenu<SelectColorContext> {

    public SelectColorMenu(MCCosmeticsPlugin core, MenuManager manager, String type) {
        super(core, manager, new MenuProp(core, "menus/color_picker", "Menu", null));
    }

    @Override
    public EditableMenuBuilder<SelectColorContext> build(EditableMenuBuilder<SelectColorContext> builder) {
        

        return builder;
    }

    public static class ColorSwatch implements MenuData<SelectColorContext> {

        
        
        @Override
        public Icon<SelectColorContext> getIcon() {
            // TODO Auto-generated method stub
            return null;
        }
        
    }
    
}
