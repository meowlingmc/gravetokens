package de.saschat.gravetokens.mixin;

import de.saschat.gravetokens.GraveTokens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.*;
import java.util.UUID;

@Mixin(Player.class)
public abstract class PlayerMixin {
    @Redirect(method = "dropEquipment", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;dropAll()V"))
    public void die(Inventory instance) {
        Player us = instance.player;
        GameRules rules = us.level().getGameRules();
        if(rules.getBoolean(GraveTokens.CREATE_GRAVES)) {
            if(us.level().isClientSide())
                return;
            ListTag inventory = new ListTag();
            instance.save(inventory);
            try {
                UUID uid = us.getUUID();
                UUID grave = UUID.randomUUID();

                BlockPos signPos = us.getOnPos().above();

                CompoundTag com = new CompoundTag();

                com.put("id", StringTag.valueOf(grave.toString()));
                com.put("inventory", inventory);
                com.put("player", StringTag.valueOf(uid.toString()));
                com.put("positionX", LongTag.valueOf(signPos.getX()));
                com.put("positionY", LongTag.valueOf(signPos.getY()));
                com.put("positionZ", LongTag.valueOf(signPos.getZ()));
                com.put("redeemed", ByteTag.valueOf((byte) 0));

                File gravesDir = new File(FabricLoader.getInstance().getGameDir().toFile(), "graves");
                gravesDir.mkdirs();

                File graveFile = new File(gravesDir, grave + ".dat");
                DataOutputStream stream = new DataOutputStream(new FileOutputStream(graveFile));
                com.write(stream);
                stream.flush();
                stream.close();

                var state = Blocks.CRIMSON_SIGN.defaultBlockState();
                us.level().setBlock(signPos, state, 3);
                SignBlockEntity entity;
                us.level().setBlockEntity(entity = new SignBlockEntity(signPos, state));

                Component[] msgs = new Component[] {
                    Component.literal("Click to redeem this grave").setStyle(Style.EMPTY.withClickEvent(
                        new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/grave redeem " + grave)
                    )),
                    Component.literal("Player: ").append(us.getDisplayName()),
                    Component.literal(""),
                    Component.literal("")
                };

                entity.setText(new SignText(
                    msgs, msgs,
                    DyeColor.BLACK,
                    true
                ), true);

                MutableComponent component = Component.literal("A grave has been created.");
                us.sendSystemMessage(component);
            } catch (Throwable e) {
                e.printStackTrace();
                System.out.println(inventory);
            }
        } else instance.dropAll();
    }
}
