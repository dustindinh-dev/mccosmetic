package io.lumine.cosmetics.nms;

import com.google.common.collect.Maps;
import io.lumine.cosmetics.MCCosmeticsPlugin;
import io.lumine.cosmetics.api.cosmetics.Cosmetic;
import io.lumine.cosmetics.api.players.wardrobe.Mannequin;
import io.lumine.cosmetics.api.players.wardrobe.WardrobeTracker;
import io.lumine.cosmetics.managers.back.BackAccessory;
import io.lumine.cosmetics.managers.gestures.Gesture;
import io.lumine.cosmetics.managers.hats.Hat;
import io.lumine.cosmetics.managers.offhand.Offhand;
import io.lumine.cosmetics.managers.sprays.Spray;
import io.lumine.cosmetics.nms.cosmetic.VolatileCosmeticHelper;
import io.lumine.cosmetics.nms.v1_19_R2.cosmetic.*;
import io.lumine.cosmetics.nms.v1_19_R2.network.VolatileChannelHandler;
import io.lumine.cosmetics.nms.v1_19_R2.wardrobe.MannequinEntity;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import lombok.Getter;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.entity.LevelEntityGetter;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_19_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_19_R2.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.v1_19_R2.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;

public class VolatileCodeEnabled_v1_19_R2 implements VolatileCodeHandler {

    @Getter private final MCCosmeticsPlugin plugin;
    private final Map<Class<? extends Cosmetic>, VolatileCosmeticHelper> cosmeticHelpers = Maps.newConcurrentMap();
    
    private Method entityGetter;

    public VolatileCodeEnabled_v1_19_R2(MCCosmeticsPlugin plugin) {
        this.plugin = plugin;
        cosmeticHelpers.put(Hat.class, new VolatileHatImpl(plugin, this));
        cosmeticHelpers.put(BackAccessory.class, new VolatileBackImpl(plugin, this));
        cosmeticHelpers.put(Spray.class, new VolatileSprayImpl(plugin, this));
        cosmeticHelpers.put(Offhand.class, new VolatileOffhandImpl(plugin, this));
        cosmeticHelpers.put(Gesture.class, new VolatileGestureImpl(plugin, this));
        
        for(var method : ServerLevel.class.getMethods()) {
            if(LevelEntityGetter.class.isAssignableFrom(method.getReturnType()) && method.getReturnType() != LevelEntityGetter.class) {
                entityGetter = method;
                break;
            }
        }
    }

    @Override
    public VolatileCosmeticHelper getCosmeticHelper(Class<? extends Cosmetic> tClass) {
        return cosmeticHelpers.get(tClass);
    }

    @Override
    public Collection<VolatileCosmeticHelper> getCosmeticHelpers() {
        return cosmeticHelpers.values();
    }

    @Override
    public void injectPlayer(Player player) {
        ServerPlayer ply = ((CraftPlayer) player).getHandle();
        VolatileChannelHandler cdh = new VolatileChannelHandler(player, this);

        ChannelPipeline pipeline = ply.connection.getConnection().channel.pipeline();
        for (String name : pipeline.toMap().keySet()) {
            if (pipeline.get(name) instanceof Connection) {
                pipeline.addBefore(name, "mc_cosmetics_packet_handler", cdh);
                break;
            }
        }
    }

    @Override
    public void removePlayer(Player player) {
        Channel channel = ((CraftPlayer) player).getHandle().connection.getConnection().channel;
        channel.eventLoop().submit(() -> {
            channel.pipeline().remove("mc_cosmetics_packet_handler");
            return null;
        });
    }

    public void broadcast(Packet<?>... packets) {
        for(Player player : Bukkit.getOnlinePlayers()) {
            var connection = ((CraftPlayer) player).getHandle().connection;
            for(Packet<?> packet : packets) {
                connection.send(packet);
            }
        }
    }

    public void broadcast(Player player, Packet<?>... packets) {
        var connection = ((CraftPlayer) player).getHandle().connection;
        for(Packet<?> packet : packets) {
            connection.send(packet);
        }
    }

    public void broadcastAroundAndSelf(Player wearer, Packet<?>... packets) {
        final var level = ((CraftWorld) wearer.getWorld()).getHandle();
        final var trackedEntity = level.getChunkSource().chunkMap.entityMap.get(wearer.getEntityId());

        if(trackedEntity == null) {
            broadcast(wearer.getWorld(), packets);
            return;
        }

        for(Packet<?> packet : packets)
            trackedEntity.broadcastAndSend(packet);
    }

    public void broadcastAround(Player wearer, Packet<?>... packets) {
        final var level = ((CraftWorld) wearer.getWorld()).getHandle();
        final var trackedEntity = level.getChunkSource().chunkMap.entityMap.get(wearer.getEntityId());

        if(trackedEntity == null) {
            broadcast(wearer.getWorld(), packets);
            return;
        }

        for(Packet<?> packet : packets)
            trackedEntity.broadcast(packet);
    }

    public void broadcast(World world, Packet<?>... packets) {
        for(Player player : world.getPlayers()) {
            var connection = ((CraftPlayer) player).getHandle().connection;
            for(Packet<?> packet : packets) {
                connection.send(packet);
            }
        }
    }

    public Entity getEntity(World world, int id) {
        ServerLevel level = ((CraftWorld) world).getHandle();
        
        try {
            var getter = entityGetter != null ?
                    (LevelEntityGetter<net.minecraft.world.entity.Entity>) entityGetter.invoke(level) :
                    level.entityManager.getEntityGetter();

            final var entity = getter.get(id);
            return entity == null ? null : entity.getBukkitEntity();
        } catch(Exception | Error ex) {
            ex.printStackTrace();
            return null;
        }
    }
    
    @Override
    public Mannequin createMannequin(WardrobeTracker tracker, Player player, Location location) {
        return new MannequinEntity(tracker,this,player,location);
    }
    
    @Override
    public void removeFakeEntity(int id) {
        ClientboundRemoveEntitiesPacket packet = new ClientboundRemoveEntitiesPacket(id);
        broadcast(packet);
    }

    @Override
    public void setBodyYaw(LivingEntity entity, double yaw) {
        ((CraftLivingEntity) entity).getHandle().yBodyRot = (float) yaw;
    }

    @Override
    public float getBodyYaw(LivingEntity entity) {
        return ((CraftLivingEntity) entity).getHandle().yBodyRot;
    }
}
