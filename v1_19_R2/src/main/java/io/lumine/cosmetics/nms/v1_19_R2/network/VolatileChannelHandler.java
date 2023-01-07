package io.lumine.cosmetics.nms.v1_19_R2.network;

import io.lumine.cosmetics.nms.VolatileCodeEnabled_v1_19_R2;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import lombok.Getter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class VolatileChannelHandler extends ChannelDuplexHandler {

	private final VolatileCodeEnabled_v1_19_R2 nmsHandler;
	@Getter private final Player player;

	public VolatileChannelHandler(Player player, VolatileCodeEnabled_v1_19_R2 nmsHandler) {
		this.player = player;
		this.nmsHandler = nmsHandler;
	}

	@Override
	public void write(ChannelHandlerContext ctx, Object obj, ChannelPromise promise) {

		try {

			List<Object> packets = new ArrayList<>();

			for(final var helper : nmsHandler.getCosmeticHelpers()) {
				final var writes = helper.write(player, obj);
				if(writes != null)
					packets.addAll(writes);
			}

			if(!packets.contains(obj))
				super.write(ctx, obj, promise);
			packets.remove(obj);

			for (var p : packets) {
				super.write(ctx, p, promise.channel().newPromise());
			}
		}catch (Exception e) {
			e.printStackTrace();
		}

	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object obj) throws Exception {

		boolean isCanceled = false;
		for(final var helper : nmsHandler.getCosmeticHelpers()) {
			isCanceled |= !helper.read(player, obj, isCanceled);
		}

		if(!isCanceled)
			super.channelRead(ctx, obj);
	}

}
