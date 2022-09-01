/*
 * This file is generated by jOOQ.
 */
package io.lumine.cosmetics.storage.sql.mappings;


import io.lumine.cosmetics.storage.sql.mappings.tables.MccosmeticsProfileEquipped;

import io.lumine.utils.lib.jooq.Index;
import io.lumine.utils.lib.jooq.OrderField;
import io.lumine.utils.lib.jooq.impl.DSL;
import io.lumine.utils.lib.jooq.impl.Internal;


/**
 * A class modelling indexes of tables in the default schema.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Indexes {

    // -------------------------------------------------------------------------
    // INDEX definitions
    // -------------------------------------------------------------------------

    public static final Index MCCOSMETICS_PROFILE_EQUIPPED_MCCOSMETICS_PROFILE_EQUIPPED_COSMETIC_ID_INDEX = Internal.createIndex(DSL.name("mccosmetics_profile_equipped_cosmetic_id_index"), MccosmeticsProfileEquipped.MCCOSMETICS_PROFILE_EQUIPPED, new OrderField[] { MccosmeticsProfileEquipped.MCCOSMETICS_PROFILE_EQUIPPED.COSMETIC_ID }, false);
}
