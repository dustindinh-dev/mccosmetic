package io.lumine.cosmetics.managers;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import com.google.common.collect.Maps;

import io.lumine.cosmetics.MCCosmeticsPlugin;
import io.lumine.utils.plugin.ReloadableModule;
import lombok.Getter;

public class CosmeticsExecutor extends ReloadableModule<MCCosmeticsPlugin> {

    @Getter private final Map<String,MCCosmeticsManager> cosmeticManagers = Maps.newConcurrentMap();
    
    public CosmeticsExecutor(MCCosmeticsPlugin plugin) {
        super(plugin);
    }

    @Override
    public void load(MCCosmeticsPlugin plugin) {}

    @Override
    public void unload() {
        cosmeticManagers.clear();
    }
    
    public Optional<MCCosmeticsManager> getManager(String type) {
        return Optional.ofNullable(cosmeticManagers.getOrDefault(type, null));
    }
    
    public <T extends MCCosmeticsManager> void registerCosmeticManager(String type, T manager) {
        type = type.toUpperCase();
        
        if(cosmeticManagers.containsKey(type)) {
            throw new IllegalStateException("Multiple managers cannot be created for the same cosmetic type");
        }
        cosmeticManagers.put(type, manager);
    }

    public Collection<String> getRegisteredTypes() {
        return cosmeticManagers.keySet();
    }
    
}
