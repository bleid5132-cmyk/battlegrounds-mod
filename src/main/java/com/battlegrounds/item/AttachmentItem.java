package com.battlegrounds.item;

import com.battlegrounds.weapon.AttachmentType;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AttachmentItem extends Item {
    public final AttachmentType type;

    public AttachmentItem(Settings settings, AttachmentType type) {
        super(settings);
        this.type = type;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("slot.battlegrounds." + type.slot.key).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.battlegrounds.att." + type.id).formatted(Formatting.DARK_GREEN));
        tooltip.add(Text.translatable("tooltip.battlegrounds.att_hint").formatted(Formatting.DARK_GRAY));
    }
}
