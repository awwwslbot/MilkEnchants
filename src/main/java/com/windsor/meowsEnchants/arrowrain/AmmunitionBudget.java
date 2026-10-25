package com.windsor.meowsEnchants.arrowrain;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** Reserves a shot's rain ammunition while the shooter still owns the executing region. */
final class AmmunitionBudget {
    private static final NamespacedKey INFINITY = NamespacedKey.minecraft("infinity");
    private AmmunitionBudget() {}

    static boolean isFreeShot(AbstractArrow source, ItemStack bow, ItemStack ammunition) {
        // Native useAmmo marks free ammunition INTANGIBLE_PROJECTILE. AbstractArrow consumes
        // that component during construction and sets CREATIVE_ONLY before the shoot event.
        if (source.getPickupStatus() == AbstractArrow.PickupStatus.CREATIVE_ONLY) return true;
        // Retain vanilla Infinity on normal arrows even if another shoot listener changes pickup.
        return ammunition != null && ammunition.getType() == Material.ARROW
                && bow.getEnchantments().entrySet().stream().anyMatch(entry ->
                        entry.getValue() > 0 && INFINITY.equals(entry.getKey().getKey()));
    }

    static int reserve(Player player, ItemStack ammunition, boolean nativeFree, int maximum) {
        if (nativeFree || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return maximum;
        }
        if (ammunition == null || ammunition.getAmount() <= 0 || ammunition.getType() == Material.AIR) return 0;
        PlayerInventory inventory = player.getInventory();
        int held = inventory.getHeldItemSlot();
        int remaining = take(inventory, 40, ammunition, maximum); // Native held-projectile order.
        remaining = take(inventory, held, ammunition, remaining);
        for (int slot = 0; slot < 36 && remaining > 0; slot++) {
            if (slot != held) remaining = take(inventory, slot, ammunition, remaining);
        }
        return maximum - remaining;
    }

    private static int take(PlayerInventory inventory, int slot, ItemStack ammunition, int remaining) {
        if (remaining <= 0) return 0;
        ItemStack stack = inventory.getItem(slot);
        if (stack == null || stack.getAmount() <= 0 || !ammunition.isSimilar(stack)) return remaining;
        int taken = Math.min(remaining, stack.getAmount());
        int left = stack.getAmount() - taken;
        ItemStack replacement = null;
        if (left > 0) {
            replacement = stack.clone();
            replacement.setAmount(left);
        }
        inventory.setItem(slot, replacement);
        return remaining - taken;
    }
}
