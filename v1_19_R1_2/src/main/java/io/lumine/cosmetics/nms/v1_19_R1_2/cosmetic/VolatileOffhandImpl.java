package io.lumine.cosmetics.nms.v1_19_R1_2.cosmetic;

import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;
import io.lumine.cosmetics.MCCosmeticsPlugin;
import io.lumine.cosmetics.api.cosmetics.ItemCosmetic;
import io.lumine.cosmetics.api.players.CosmeticProfile;
import io.lumine.cosmetics.managers.offhand.Offhand;
import io.lumine.cosmetics.nms.VolatileCodeEnabled_v1_19_R1_2;
import io.lumine.cosmetics.nms.cosmetic.VolatileEquipmentHelper;
import io.lumine.cosmetics.players.Profile;
import io.netty.buffer.Unpooled;
import lombok.Getter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundAddPlayerPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.world.entity.EquipmentSlot;
import org.bukkit.craftbukkit.v1_19_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_19_R1.inventory.CraftItemStack;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public class VolatileOffhandImpl implements VolatileEquipmentHelper {

    @Getter private final MCCosmeticsPlugin plugin;
    private final VolatileCodeEnabled_v1_19_R1_2 nmsHandler;
    private final Map<Integer, Player> playerTracker = Maps.newConcurrentMap();

    public VolatileOffhandImpl(MCCosmeticsPlugin plugin, VolatileCodeEnabled_v1_19_R1_2 nmsHandler) {
        this.plugin = plugin;
        this.nmsHandler = nmsHandler;
    }
    
    @Override
    public void apply(CosmeticProfile profile) {

        if (profile == null)
            return;

        Player player = profile.getPlayer();
       
        final var maybeEquipped = profile.getEquipped(Offhand.class);
        if(maybeEquipped.isEmpty()) {
            return;
        }
        var equip = maybeEquipped.get();
        var opt = equip.getCosmetic();
        
        if(!(opt instanceof ItemCosmetic offhand))
            return;

        var nmsOffhand = CraftItemStack.asNMSCopy(offhand.getCosmetic(equip));

        playerTracker.put(player.getEntityId(), player);

        ClientboundSetEquipmentPacket equipmentPacket = new ClientboundSetEquipmentPacket(player.getEntityId(), List.of(Pair.of(EquipmentSlot.OFFHAND, nmsOffhand)));

        nmsHandler.broadcastAroundAndSelf(player, equipmentPacket);

    }

    @Override
    public void unapply(CosmeticProfile profile) {
        final var nmsPlayer = ((CraftPlayer) profile.getPlayer()).getHandle();
        final var item = nmsPlayer.getItemBySlot(EquipmentSlot.OFFHAND);
        ClientboundSetEquipmentPacket equipmentPacket = new ClientboundSetEquipmentPacket(nmsPlayer.getId(), List.of(Pair.of(EquipmentSlot.OFFHAND, item)));
        nmsHandler.broadcastAroundAndSelf(nmsPlayer.getBukkitEntity(), equipmentPacket);
    }

    @Override
    public List<Object> write(Player receiver, Object packet) {
        if(packet instanceof ClientboundAddPlayerPacket playerPacket) {
            int id = playerPacket.getEntityId();
            Profile profile = getProfile(receiver, id);
            if(profile != null && !profile.isHidden(Offhand.class))
                handleSpawn(profile);
        }else if(packet instanceof ClientboundSetEquipmentPacket equipmentPacket) {
            int id = equipmentPacket.getEntity();
            Profile profile = getProfile(receiver, id);
            if(profile != null && !profile.isHidden(Offhand.class))
                handleSpawn(profile);
        }

        return null;
    }

    private Profile getProfile(Player receiver, int id) {
        final var entity = nmsHandler.getEntity(receiver.getWorld(), id);
        if(!(entity instanceof Player player))
            return null;
        return plugin.getProfiles().getProfile(player);
    }

    public void handleSpawn(Profile profile) {
        final var maybeEquipped = profile.getEquipped(Offhand.class);
        if(maybeEquipped.isEmpty()) {
            return;
        }
        var equip = maybeEquipped.get();
        var opt = equip.getCosmetic();
        
        if(!(opt instanceof ItemCosmetic offhand))
            return;

        final var player = profile.getPlayer();
        final var nmsOffhand = CraftItemStack.asNMSCopy(offhand.getCosmetic(equip));
        ClientboundSetEquipmentPacket equipmentPacket = new ClientboundSetEquipmentPacket(player.getEntityId(), List.of(Pair.of(EquipmentSlot.OFFHAND, nmsOffhand)));

        FriendlyByteBuf byteBuf = new FriendlyByteBuf(Unpooled.buffer());
        byteBuf.writeByte(80);
        equipmentPacket.write(byteBuf);

        final var pipeline = ((CraftPlayer) player).getHandle().connection.getConnection().channel.pipeline();
        pipeline.writeAndFlush(equipmentPacket);
    }

}
