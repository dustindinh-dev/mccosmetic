package io.lumine.cosmetics.nms.v1_19_R2.wardrobe;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_19_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_19_R2.entity.CraftPlayer;
import org.bukkit.entity.Player;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;

import io.lumine.cosmetics.api.cosmetics.Cosmetic;
import io.lumine.cosmetics.api.players.wardrobe.Mannequin;
import io.lumine.cosmetics.api.players.wardrobe.WardrobeTracker;
import io.lumine.cosmetics.nms.VolatileCodeEnabled_v1_19_R2;
import io.netty.buffer.Unpooled;
import lombok.Getter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundAddPlayerPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;

public class MannequinEntity implements Mannequin {

    private final VolatileCodeEnabled_v1_19_R2 handler;
    @Getter private final WardrobeTracker tracker;
    @Getter private final UUID uniqueId;
    @Getter private final Player player;
    @Getter private final Location location;
    @Getter private float rotation;
    
    @Getter private ServerPlayer fakePlayer;
    private Map<Class<? extends Cosmetic>,Integer> extraEntities = Maps.newConcurrentMap();

    public MannequinEntity(WardrobeTracker tracker, VolatileCodeEnabled_v1_19_R2 handler, Player player, Location location) {
        this.tracker = tracker;
        this.handler = handler;
        this.player = player;
        this.location = location;
        this.uniqueId = UUID.randomUUID();
        
        var serverPlayer = ((CraftPlayer) player).getHandle();
        var level = ((CraftWorld) location.getWorld()).getHandle();

        this.rotation = serverPlayer.getYRot() + 180;
        
        if(rotation > 180) { 
            rotation = rotation - 360;
        }
        
        var gameProfile = serverPlayer.getGameProfile();
        
        gameProfile = new GameProfile(uniqueId, "Wardrobe");
        gameProfile.getProperties().putAll(serverPlayer.getGameProfile().getProperties());
        
        this.fakePlayer = new ServerPlayer(level.getServer(), level, gameProfile);
        
        var spawn = new FriendlyByteBuf(Unpooled.buffer());
        
        spawn.writeVarInt(fakePlayer.getId());
        spawn.writeUUID(fakePlayer.getUUID());
        spawn.writeDouble(location.getX());
        spawn.writeDouble(location.getY());
        spawn.writeDouble(location.getZ());
        spawn.writeByte((byte)(int)(serverPlayer.getYRot() * 256.0F / 360.0F));
        spawn.writeByte((byte)(int)(serverPlayer.getXRot() * 256.0F / 360.0F));

        var packetPlayerInfo = new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, fakePlayer);
        var packetAddPlayer = new ClientboundAddPlayerPacket(spawn);

        var equipmentPacket = new ClientboundSetEquipmentPacket(fakePlayer.getId(), 
                List.of(Pair.of(EquipmentSlot.HEAD, serverPlayer.getItemBySlot(EquipmentSlot.HEAD)),
                        Pair.of(EquipmentSlot.CHEST, serverPlayer.getItemBySlot(EquipmentSlot.CHEST)),
                        Pair.of(EquipmentSlot.LEGS, serverPlayer.getItemBySlot(EquipmentSlot.LEGS)),
                        Pair.of(EquipmentSlot.FEET, serverPlayer.getItemBySlot(EquipmentSlot.FEET)),
                        Pair.of(EquipmentSlot.MAINHAND, serverPlayer.getItemBySlot(EquipmentSlot.MAINHAND)),
                        Pair.of(EquipmentSlot.OFFHAND, serverPlayer.getItemBySlot(EquipmentSlot.OFFHAND))));

        var head = (byte)(int)(rotation * 256.0F / 360.0F);
        var rotatePacket = new ClientboundRotateHeadPacket(fakePlayer, head);
        
        var swingPacket = new ClientboundAnimatePacket(fakePlayer, 0);
        
        handler.broadcast(player, packetPlayerInfo, packetAddPlayer, equipmentPacket, rotatePacket, swingPacket);
        
        for(var eq : handler.getPlugin().getProfiles().getProfile(player).getEquipped().values()) {
            eq.getCosmetic().getManager().equipMannequin(this, eq);
        }
    }
    
    public void despawn() {
        var packetPlayerInfo = new ClientboundPlayerInfoRemovePacket(Lists.newArrayList(fakePlayer.getUUID()));
        var packetRemovePlayer = new ClientboundRemoveEntitiesPacket(fakePlayer.getId());
        
        handler.broadcast(player, packetPlayerInfo, packetRemovePlayer);
        
        for(var i : extraEntities.values()) {
            var removePacket = new ClientboundRemoveEntitiesPacket(i);
            handler.broadcast(player, removePacket);
        }
    }
    
    public void addExtraEntity(Class<? extends Cosmetic> type, int id) {
        extraEntities.put(type, id);
    }
    
    public void removeExtraEntity(Class<? extends Cosmetic> type) {
        var id = extraEntities.remove(type);
        
        if(id != null) {
            var removePacket = new ClientboundRemoveEntitiesPacket(id);
            handler.broadcast(player, removePacket);
        }
    }
    
    public int getEntityId() {
        return fakePlayer.getId();
    }

}
