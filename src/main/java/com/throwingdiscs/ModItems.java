package com.throwingdiscs;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModItems {
    public static final Item IRON_THROWING_DISC = register(DiscTier.IRON);
    public static final Item GOLD_THROWING_DISC = register(DiscTier.GOLD);
    public static final Item DIAMOND_THROWING_DISC = register(DiscTier.DIAMOND);
    public static final Item NETHERITE_THROWING_DISC = register(DiscTier.NETHERITE);

    private ModItems() {}

    private static Item register(DiscTier tier) {
        Item.Settings settings = new Item.Settings().maxDamage(tier.durability);
        if (tier == DiscTier.NETHERITE) settings.fireproof();
        return Registry.register(Registries.ITEM,
                new Identifier(ThrowingDiscsMod.MOD_ID, tier.itemName()),
                new ThrowingDiscItem(tier, settings));
    }

    public static void init() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
            entries.add(IRON_THROWING_DISC);
            entries.add(GOLD_THROWING_DISC);
            entries.add(DIAMOND_THROWING_DISC);
            entries.add(NETHERITE_THROWING_DISC);
        });
    }
}
