package de.saschat.gravetokens;

import com.google.common.io.Files;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import org.apache.logging.log4j.core.net.DatagramOutputStream;

import java.io.*;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;


public class GraveTokens implements DedicatedServerModInitializer, ModInitializer {
    public static final GameRules.Key<GameRules.BooleanValue> CREATE_GRAVES = GameRuleRegistry.register("createGraves", GameRules.Category.PLAYER, GameRuleFactory.createBooleanRule(true));
    @Override
    public void onInitializeServer() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("grave").then(
                literal("redeem").then(argument("grave", StringArgumentType.string()).executes(ctx -> {
                    if(!ctx.getSource().isPlayer())
                        return 0;

                    String grave = StringArgumentType.getString(ctx, "grave");
                    if(grave.contains("\\\\|\\/|\\."))
                        return 0;

                    File graveFile = new File(new File(FabricLoader.getInstance().getGameDir().toFile(), "graves"), grave + ".dat");
                    if(!graveFile.exists()) {
                        ctx.getSource().sendFailure(Component.literal("Grave does not exist."));
                        return 0;
                    }

                    try {
                        DataInputStream stream = new DataInputStream(new FileInputStream(graveFile));
                        CompoundTag data = CompoundTag.TYPE.load(stream, NbtAccounter.unlimitedHeap());
                        stream.close();

                        ByteTag redemeed = (ByteTag) data.get("redeemed");
                        if(redemeed.getAsByte() == 1) {
                            ctx.getSource().sendFailure(Component.literal("Grave already redeemed."));
                            return 0;
                        }

                        data.put("redeemed", ByteTag.valueOf((byte) 1));
                        ListTag tag = (ListTag) data.get("inventory");
                        Inventory inventory = ctx.getSource().getPlayer().getInventory();

                        inventory.dropAll();
                        inventory.load(tag);

                        int x = ((LongTag) data.get("positionX")).getAsInt();
                        int y = ((LongTag) data.get("positionY")).getAsInt();
                        int z = ((LongTag) data.get("positionZ")).getAsInt();

                        BlockPos sign = new BlockPos(x, y, z);
                        if(ctx.getSource().getLevel().getBlockState(sign).getBlock() == Blocks.CRIMSON_SIGN)
                            ctx.getSource().getLevel().setBlock(sign, Blocks.AIR.defaultBlockState(), 3);

                        try {
                            DataOutputStream outstream = new DataOutputStream(new FileOutputStream(graveFile));
                            data.write(outstream);
                            outstream.flush();
                            outstream.close();
                        } catch (Exception ex) {
                            ex.printStackTrace(); // Dupe bug?
                        }

                        ctx.getSource().sendSuccess(() -> Component.literal("Grave successfully redeemed."), false);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    return 0;
                }))
            ));
        });
    }

    @Override
    public void onInitialize() {

    }
}
