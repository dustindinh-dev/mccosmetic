package io.lumine.cosmetics.nms.v1_19_R2.cosmetic;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import io.lumine.cosmetics.MCCosmeticsPlugin;
import io.lumine.cosmetics.api.cosmetics.manager.HideableCosmetic;
import io.lumine.cosmetics.api.players.CosmeticProfile;
import io.lumine.cosmetics.managers.gestures.Gesture;
import io.lumine.cosmetics.managers.gestures.GestureManager;
import io.lumine.cosmetics.managers.gestures.QuitMethod;
import io.lumine.cosmetics.nms.VolatileCodeEnabled_v1_19_R2;
import io.lumine.cosmetics.nms.cosmetic.VolatileEquipmentHelper;
import io.lumine.cosmetics.players.Profile;
import io.netty.buffer.Unpooled;
import lombok.Getter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.item.ItemStack;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.v1_19_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_19_R2.attribute.CraftAttributeMap;
import org.bukkit.craftbukkit.v1_19_R2.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class VolatileGestureImpl implements VolatileEquipmentHelper {

	private static final List<Pair<EquipmentSlot, ItemStack>> empty = new ArrayList<>();

	static {
		for(final var slot : EquipmentSlot.values())
			empty.add(Pair.of(slot, ItemStack.EMPTY));
	}

	@Getter
	private final MCCosmeticsPlugin plugin;
	private final VolatileCodeEnabled_v1_19_R2 nmsHandler;
	private final Set<Player> activeProfile = Sets.newConcurrentHashSet();
	private final Map<Integer, Player> playerTracker = Maps.newConcurrentMap();

	private Horse horse;

	public VolatileGestureImpl(MCCosmeticsPlugin plugin, VolatileCodeEnabled_v1_19_R2 nmsHandler) {
		this.plugin = plugin;
		this.nmsHandler = nmsHandler;
	}

	@Override
	public void apply(CosmeticProfile profile) {
		if (profile == null)
			return;
		Player player = profile.getPlayer();
		if(activeProfile.contains(player))
			return;

        final var maybeEquipped = profile.getEquipped(Gesture.class);
        if(maybeEquipped.isEmpty()) {
            return;
        }
        var opt = maybeEquipped.get().getCosmetic();
        
        if(!(opt instanceof Gesture gesture))
            return;

		playerTracker.put(player.getEntityId(), player);
		activeProfile.add(player);
		if(!gesture.isCanMove())
			getHorsed(player);
		player.setInvisible(true);

		for(final var value : ((Profile) profile).getEquipped().values()) {
			final var manager = value.getCosmetic().getManager();
			if(!(manager instanceof HideableCosmetic hide))
				continue;
			hide.hide(profile, gesture);
		}
		ClientboundSetEquipmentPacket equipmentPacket = new ClientboundSetEquipmentPacket(player.getEntityId(), empty);
		nmsHandler.broadcastAroundAndSelf(player, equipmentPacket);
	}

	@Override
	public void unapply(CosmeticProfile profile) {
		if (profile == null)
			return;
		Player player = profile.getPlayer();
		if(!activeProfile.contains(player))
			return;

		activeProfile.remove(player);
		playerTracker.remove(player.getEntityId());
		nmsHandler.broadcast(player, new ClientboundRemoveEntitiesPacket(horse.getId()));

		final var nmsPlayer = ((CraftPlayer) player).getHandle();
		List<Pair<EquipmentSlot, ItemStack>> equipment = new ArrayList<>();
		for(EquipmentSlot slot : EquipmentSlot.values())
			equipment.add(Pair.of(slot, nmsPlayer.getItemBySlot(slot)));
		ClientboundSetEquipmentPacket equipmentPacket = new ClientboundSetEquipmentPacket(player.getEntityId(), equipment);
		nmsHandler.broadcastAroundAndSelf(player, equipmentPacket);

		player.setInvisible(false);
		for(final var value : ((Profile) profile).getEquipped().values()) {
			final var manager = value.getCosmetic().getManager();
			if(!(manager instanceof HideableCosmetic hide))
				continue;
			hide.show(profile);
		}
	}

	private ClientboundSetPassengersPacket createPassengerPacket(int mount, int... driver) {
		FriendlyByteBuf bb = new FriendlyByteBuf(Unpooled.buffer());
		bb.writeVarInt(mount);
		bb.writeVarIntArray(driver);
		return new ClientboundSetPassengersPacket(bb);
	}

	private void getHorsed(Player player) {
		final var nmsPlayer = ((CraftPlayer) player).getHandle();

		if(horse == null) {
			horse = new Horse(EntityType.HORSE, ((CraftWorld) player.getWorld()).getHandle());
			horse.setInvisible(true);
			horse.setHealth(0);
			horse.getAttribute(CraftAttributeMap.toMinecraft(Attribute.GENERIC_MAX_HEALTH)).setBaseValue(0);
		}
		horse.setPos(nmsPlayer.getX(), nmsPlayer.getY() - horse.getPassengersRidingOffset() - nmsPlayer.getMyRidingOffset(), nmsPlayer.getZ());

		var mobPacket = new ClientboundAddEntityPacket(horse);
		var dataPacket = new ClientboundSetEntityDataPacket(horse.getId(), horse.getEntityData().getNonDefaultValues());
		var attributesPacket = new ClientboundUpdateAttributesPacket(horse.getId(), horse.getAttributes().getSyncableAttributes());
		var passengersPacket = createPassengerPacket(horse.getId(), nmsPlayer.getId());

		nmsHandler.broadcast(player, mobPacket, dataPacket, attributesPacket, passengersPacket);
	}

	@Override
	public boolean read(Player sender, Object packet, boolean isCanceled) {
		if(!plugin.getGestureManager().getTicking().containsKey(sender)) {
		    return true;
		}

		if(packet instanceof ServerboundPlayerInputPacket inputPacket) {
			final var manager = (GestureManager) plugin.getGestureManager();
			if(inputPacket.isShiftKeyDown())
				manager.quit(sender, QuitMethod.SNEAK);
			if(inputPacket.isJumping())
				manager.quit(sender, QuitMethod.JUMP);
		}else if(packet instanceof ServerboundSetCarriedItemPacket setSlotPacket) {
			int oSlot = sender.getInventory().getHeldItemSlot();
			if(oSlot != setSlotPacket.getSlot()) {
				nmsHandler.broadcast(sender, new ClientboundSetCarriedItemPacket(oSlot));
				return false;
			}
		}
		return true;
	}

	@Override
	public List<Object> write(Player receiver, Object packet) {
		if(packet instanceof ClientboundSetEquipmentPacket equipmentPacket) {
			int id = equipmentPacket.getEntity();
			final var spawnedPlayer = playerTracker.get(id);
			if(spawnedPlayer == null)
				return null;
			final var profile = MCCosmeticsPlugin.inst().getProfiles().getProfile(spawnedPlayer);
			if(profile != null)
				return List.of(new ClientboundSetEquipmentPacket(id, empty));
		}
		return null;
	}
}
