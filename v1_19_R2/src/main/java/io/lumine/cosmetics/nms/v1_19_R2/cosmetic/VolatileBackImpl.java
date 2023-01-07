package io.lumine.cosmetics.nms.v1_19_R2.cosmetic;

import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;
import io.lumine.cosmetics.MCCosmeticsPlugin;
import io.lumine.cosmetics.api.cosmetics.Cosmetic;
import io.lumine.cosmetics.api.cosmetics.EquippedCosmetic;
import io.lumine.cosmetics.api.cosmetics.ItemCosmetic;
import io.lumine.cosmetics.api.players.CosmeticProfile;
import io.lumine.cosmetics.api.players.wardrobe.Mannequin;
import io.lumine.cosmetics.constants.CosmeticType;
import io.lumine.cosmetics.logging.MCLogger;
import io.lumine.cosmetics.managers.back.BackAccessory;
import io.lumine.cosmetics.managers.offhand.Offhand;
import io.lumine.cosmetics.nms.VolatileCodeEnabled_v1_19_R2;
import io.lumine.cosmetics.nms.cosmetic.VolatileEquipmentHelper;
import io.lumine.cosmetics.nms.v1_19_R2.wardrobe.MannequinEntity;
import io.lumine.cosmetics.players.Profile;
import io.netty.buffer.Unpooled;
import lombok.Getter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_19_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_19_R2.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_19_R2.inventory.CraftItemStack;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class VolatileBackImpl implements VolatileEquipmentHelper {

	@Getter
	private final MCCosmeticsPlugin plugin;
	private final VolatileCodeEnabled_v1_19_R2 nmsHandler;
	private final Map<Player, ArmorStand> activeProfile = Maps.newConcurrentMap();
	private final Map<Integer, Player> playerTracker = Maps.newConcurrentMap();

	public VolatileBackImpl(MCCosmeticsPlugin plugin, VolatileCodeEnabled_v1_19_R2 nmsHandler) {
		this.plugin = plugin;
		this.nmsHandler = nmsHandler;
	}

	@Override
	public void apply(CosmeticProfile profile) {
		if (profile == null)
			return;
		Player player = profile.getPlayer();
		
		var maybeEquipped = profile.getEquipped(BackAccessory.class);
		if(maybeEquipped.isEmpty()) {
		    return;
		}
		var equipped = maybeEquipped.get();
		var cosmetic = equipped.getCosmetic();
		
		if (!(cosmetic instanceof ItemCosmetic back)) {
			return;
		}

		var nmsPlayer = ((CraftPlayer) player).getHandle();
		var nmsBack = CraftItemStack.asNMSCopy(back.getCosmetic(equipped));

		ArmorStand stand = activeProfile.get(player);
		if(stand == null) {
			playerTracker.put(player.getEntityId(), player);
			stand = new ArmorStand(EntityType.ARMOR_STAND, ((CraftWorld) player.getWorld()).getHandle());
			stand.moveTo(nmsPlayer.getX(), nmsPlayer.getY() + nmsPlayer.getPassengersRidingOffset() + stand.getMyRidingOffset(), nmsPlayer.getZ(), nmsPlayer.getYRot(), 0);
			stand.setMarker(true);
			stand.setInvisible(true);
			stand.setSilent(true);

			activeProfile.put(player, stand);

			var mobPacket = new ClientboundAddEntityPacket(stand);
			var dataPacket = new ClientboundSetEntityDataPacket(stand.getId(), stand.getEntityData().getNonDefaultValues());
			var passengersPacket = createPassengerPacket(player.getEntityId(), stand.getId());

			nmsHandler.broadcastAroundAndSelf(player, mobPacket, dataPacket, passengersPacket);
		}

		stand.setItemSlot(EquipmentSlot.HEAD, nmsBack);

		var equipmentPacket = new ClientboundSetEquipmentPacket(stand.getId(), List.of(Pair.of(EquipmentSlot.HEAD, nmsBack)));

		nmsHandler.broadcastAroundAndSelf(player, equipmentPacket);
	}

	@Override
	public void unapply(CosmeticProfile profile) {
		Player player = profile.getPlayer();
		ArmorStand stand = activeProfile.remove(player);
		if(stand == null)
			return;
		playerTracker.remove(player.getEntityId());
		ClientboundRemoveEntitiesPacket removePacket = new ClientboundRemoveEntitiesPacket(stand.getId());
		nmsHandler.broadcastAroundAndSelf(player, removePacket);
	}
	
    @Override 
    public void equipMannequin(Mannequin mannequin, EquippedCosmetic cosmetic) {
        if(!(cosmetic.getCosmetic() instanceof BackAccessory back)) {
            return;
        }
        
        final var entityId = mannequin.getEntityId();
        final var player = mannequin.getPlayer();
        var nmsBack = CraftItemStack.asNMSCopy(back.getCosmetic(cosmetic));

        var mannequinLocation = mannequin.getLocation();
        
        mannequin.removeExtraEntity(BackAccessory.class);

        var stand = new ArmorStand(EntityType.ARMOR_STAND, ((CraftWorld) player.getWorld()).getHandle());
        stand.moveTo(mannequinLocation.getX(), mannequinLocation.getY() + stand.getMyRidingOffset(), mannequinLocation.getZ(), mannequinLocation.getYaw(), 0);
        stand.setMarker(true);
        stand.setInvisible(true);
        stand.setSilent(true);

        mannequin.addExtraEntity(BackAccessory.class, stand.getId());
        
        var mobPacket = new ClientboundAddEntityPacket(stand);
        var dataPacket = new ClientboundSetEntityDataPacket(stand.getId(), stand.getEntityData().getNonDefaultValues());
        var passengersPacket = createPassengerPacket(entityId, stand.getId());

        nmsHandler.broadcast(player, mobPacket, dataPacket, passengersPacket);

        stand.setItemSlot(EquipmentSlot.HEAD, nmsBack);

        var equipmentPacket = new ClientboundSetEquipmentPacket(stand.getId(), List.of(Pair.of(EquipmentSlot.HEAD, nmsBack)));

        nmsHandler.broadcast(player, equipmentPacket);
    }

    @Override
    public void unequipMannequin(Mannequin mannequin) {
        mannequin.removeExtraEntity(BackAccessory.class);
    }

	@Override
	public boolean read(Player sender, Object packet, boolean isCanceled) {
		final var profile = MCCosmeticsPlugin.inst().getProfiles().getProfile(sender);
		if(profile == null || profile.isHidden(BackAccessory.class))
			return true;
		if(packet instanceof ServerboundMovePlayerPacket) {
			handleRotate(profile);
		}else if(packet instanceof ServerboundAcceptTeleportationPacket) {
			final var list = handleSpawn(profile);
			if(list == null)
				return true;
			final var connection = ((CraftPlayer) sender).getHandle().connection;
			for(Object obj : list) {
				connection.send((Packet<?>) obj);
			}
		}
		return true;
	}

	@Override
	public List<Object> write(Player receiver, Object packet) {
		if(packet instanceof ClientboundAddPlayerPacket playerPacket) {
			int id = playerPacket.getEntityId();
			if(playerTracker.containsKey(id)) {
				final var spawnedPlayer = playerTracker.get(id);
				final var profile = MCCosmeticsPlugin.inst().getProfiles().getProfile(spawnedPlayer);
				if(profile == null || profile.isHidden(BackAccessory.class))
					return null;
				return handleSpawn(profile);
			}
		}else if(packet instanceof ClientboundRemoveEntitiesPacket removePacket) {
			for(int id : removePacket.getEntityIds()) {
				if(playerTracker.containsKey(id)) {
					return handleDespawn(playerTracker.get(id));
				}
			}
		}
		/*
		else if(packet instanceof ClientboundMoveEntityPacket moveEntityPacket) {
			FriendlyByteBuf byteBuf = new FriendlyByteBuf(Unpooled.buffer());
			moveEntityPacket.write(byteBuf);
			int id = byteBuf.readVarInt();
			if(playerTracker.containsKey(id)) {
				final var spawnedPlayer = playerTracker.get(id);
				final var profile = MCCosmeticsPlugin.inst().getProfiles().getProfile(spawnedPlayer);
				if(profile == null || profile.isHidden(BackAccessory.class))
					return null;
				return handleMove(profile, moveEntityPacket);
			}
		}else if(packet instanceof ClientboundTeleportEntityPacket teleportEntityPacket) {
			int id = teleportEntityPacket.getId();
			if(playerTracker.containsKey(id)) {
				final var spawnedPlayer = playerTracker.get(id);
				final var profile = MCCosmeticsPlugin.inst().getProfiles().getProfile(spawnedPlayer);
				if(profile == null || profile.isHidden(BackAccessory.class))
					return null;
				return handleTeleport(profile);
			}
		}*/

		return null;
	}

	private void handleRotate(Profile profile) {
		if(!hasBack(profile))
			return;

		final var wearer = profile.getPlayer();
		final var nmsPlayer = ((CraftPlayer) wearer).getHandle();
		final var stand = activeProfile.get(wearer);

		ClientboundRotateHeadPacket packet = new ClientboundRotateHeadPacket(stand, VolatileEquipmentHelper.toByte(nmsPlayer.getYRot()));
		nmsHandler.broadcastAroundAndSelf(wearer, packet);
	}

	private List<Object> handleSpawn(Profile profile) {
		if(!hasBack(profile))
			return null;

		final var wearer = profile.getPlayer();
		final var stand = activeProfile.get(wearer);

		var mobPacket = new ClientboundAddEntityPacket(stand);
		ClientboundSetEntityDataPacket dataPacket = new ClientboundSetEntityDataPacket(stand.getId(), stand.getEntityData().getNonDefaultValues());
		ClientboundSetEquipmentPacket equipmentPacket = new ClientboundSetEquipmentPacket(stand.getId(), List.of(Pair.of(EquipmentSlot.HEAD, stand.getItemBySlot(EquipmentSlot.HEAD))));
		ClientboundSetPassengersPacket passengersPacket = createPassengerPacket(wearer.getEntityId(), stand.getId());

		return List.of(mobPacket, dataPacket, equipmentPacket, passengersPacket);
	}

	private List<Object> handleDespawn(Player player) {
		final var stand = activeProfile.get(player);
		if(stand == null)
			return null;
		ClientboundRemoveEntitiesPacket removePacket = new ClientboundRemoveEntitiesPacket(stand.getId());
		return List.of(removePacket);
	}

	private List<Object> handleMove(Profile profile, ClientboundMoveEntityPacket moveEntityPacket) {
		if(!hasBack(profile))
			return null;

		final var wearer = profile.getPlayer();
		final var stand = activeProfile.get(wearer);

		ClientboundMoveEntityPacket move;
		if(moveEntityPacket.hasPosition() && moveEntityPacket.hasRotation()) {
			move = new ClientboundMoveEntityPacket.PosRot(
					stand.getId(),
					moveEntityPacket.getXa(),
					moveEntityPacket.getYa(),
					moveEntityPacket.getZa(),
					moveEntityPacket.getyRot(),
					moveEntityPacket.getxRot(),
					false);
		}else if(moveEntityPacket.hasPosition()) {
			move = new ClientboundMoveEntityPacket.Pos(
					stand.getId(),
					moveEntityPacket.getXa(),
					moveEntityPacket.getYa(),
					moveEntityPacket.getZa(),
					false);
		}else {
			move = new ClientboundMoveEntityPacket.Rot(
					stand.getId(),
					moveEntityPacket.getyRot(),
					moveEntityPacket.getxRot(),
					false);
		}
		return List.of(move);
	}

	private List<Object> handleTeleport(Profile profile) {
		if(!hasBack(profile))
			return null;

		final var wearer = profile.getPlayer();
		final var nmsPlayer = ((CraftPlayer) wearer).getHandle();
		final var stand = activeProfile.get(wearer);
		stand.moveTo(nmsPlayer.getX(), nmsPlayer.getY() + nmsPlayer.getPassengersRidingOffset() + stand.getMyRidingOffset(), nmsPlayer.getZ(), nmsPlayer.getYRot(), 0);

		return List.of(new ClientboundTeleportEntityPacket(stand));
	}

	private boolean hasBack(Profile profile) {
		if(profile == null)
			return false;

		var maybeBack = profile.getEquipped(BackAccessory.class);
		return maybeBack.isPresent() && maybeBack.get().getCosmetic() instanceof ItemCosmetic && activeProfile.containsKey(profile.getPlayer());
	}

	private ClientboundSetPassengersPacket createPassengerPacket(int mount, int... driver) {
		FriendlyByteBuf bb = new FriendlyByteBuf(Unpooled.buffer());
		bb.writeVarInt(mount);
		bb.writeVarIntArray(driver);
		return new ClientboundSetPassengersPacket(bb);
	}

}