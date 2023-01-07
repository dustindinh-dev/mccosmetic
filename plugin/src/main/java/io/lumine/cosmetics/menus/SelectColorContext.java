package io.lumine.cosmetics.menus;

import io.lumine.cosmetics.api.cosmetics.Cosmetic;
import io.lumine.cosmetics.managers.MCCosmeticsManager;
import io.lumine.cosmetics.players.Profile;
import io.lumine.utils.serialize.Chroma;
import lombok.Data;

@Data
public class SelectColorContext {

    private final Profile profile;
    private final MCCosmeticsManager manager;
    private final Cosmetic cosmetic;
    private Chroma chroma;

}
