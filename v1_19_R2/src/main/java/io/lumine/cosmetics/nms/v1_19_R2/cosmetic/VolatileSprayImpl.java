package io.lumine.cosmetics.nms.v1_19_R2.cosmetic;

import io.lumine.cosmetics.MCCosmeticsPlugin;
import io.lumine.cosmetics.managers.sprays.Spray;
import io.lumine.cosmetics.nms.VolatileCodeEnabled_v1_19_R2;
import io.lumine.cosmetics.nms.cosmetic.VolatileSprayHelper;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.v1_19_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_19_R2.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;

public class VolatileSprayImpl implements VolatileSprayHelper {

    @Getter private final MCCosmeticsPlugin plugin;
    private final VolatileCodeEnabled_v1_19_R2 nmsHandler;

    public VolatileSprayImpl(MCCosmeticsPlugin plugin, VolatileCodeEnabled_v1_19_R2 nmsHandler) {
        this.plugin = plugin;
        this.nmsHandler = nmsHandler;
    }

    private static final ItemStack map = new ItemStack(Material.FILLED_MAP);
    private static final MapMeta mapMeta = (MapMeta) map.getItemMeta();
    
    @Override
    public int drawSpray(Spray spray, Location location, BlockFace face, int rotation) {
        var world = ((CraftWorld) location.getWorld()).getHandle();
        var pos = new BlockPos(location.getX(), location.getY(), location.getZ());

        var image = spray.getImage();
        
        mapMeta.setMapId(image.getMapNumber());
        map.setItemMeta(mapMeta);
        
        var nmsMap = CraftItemStack.asNMSCopy(map);

        final int dir = switch(face) {
            case DOWN  -> 0;
            case UP    -> 1;
            case NORTH -> 2;
            case SOUTH -> 3;
            case WEST  -> 4;
            case EAST  -> 5;
            default    -> 1;
        };
        
        var frame = new ItemFrame(world, pos, Direction.UP);
        frame.setItem(nmsMap);
        frame.setInvisible(true);
        frame.setRotation(rotation);

        var packetAdd = new ClientboundAddEntityPacket(frame, dir);
        var packetData = new ClientboundSetEntityDataPacket(frame.getId(), frame.getEntityData().getNonDefaultValues());
        var packetMap = constructMapPacket(image.getMapNumber(), image.getPixels());
        
        nmsHandler.broadcast(packetAdd, packetData, packetMap);
        
        return frame.getId();
    }
    
    private static final int startX = 0;
    private static final int startY = 0;
    private static final int mapWidth = 128;
    private static final int mapHeight = 128;
    private static final byte mapScale = 1;
    private static final boolean mapLocked = true;
    
    private ClientboundMapItemDataPacket constructMapPacket(int id, byte[] pixels) {
        var mapData = constructMapData(startX, startY, mapWidth, mapHeight, pixels);
        var packet = new ClientboundMapItemDataPacket(id, mapScale, mapLocked, null, mapData);

        return packet;
    }
    
    private MapItemSavedData.MapPatch constructMapData(int startX, int startY, int width, int height, byte[] pixels) {
        return new MapItemSavedData.MapPatch(startX, startY, width, height, pixels);
    }

}
