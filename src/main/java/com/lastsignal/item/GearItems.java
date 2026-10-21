package com.lastsignal.item;

import com.lastsignal.LastSignalMod;
import com.lastsignal.entity.HorrorEntity;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.registries.RegistryObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Player gear: the Lantern Warden armor set (a full set refuses Darkness and takes 15% less damage from the signal's creatures) and three weapons
 * that each do one thing against the creatures. Registered into the normal item registry; textures/models/recipes come from tools/gear.py.
 */
public final class GearItems {
    public static final Map<String, RegistryObject<Item>> GEAR = new LinkedHashMap<>();

    public enum Warden implements ArmorMaterial {
        INSTANCE;
        private static final int[] DUR = {13, 15, 16, 11};
        private static final int[] DEF = {2, 5, 6, 2};
        @Override public int getDurabilityForType(ArmorItem.Type t) { return DUR[t.ordinal() % 4] * 24; }
        @Override public int getDefenseForType(ArmorItem.Type t) { return DEF[t.ordinal() % 4]; }
        @Override public int getEnchantmentValue() { return 12; }
        @Override public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_IRON; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.of(Items.IRON_INGOT); }
        @Override public String getName() { return LastSignalMod.MODID + ":lantern_warden"; }
        @Override public float getToughness() { return 1.0f; }
        @Override public float getKnockbackResistance() { return 0.05f; }
    }

    private static void armor(String id, ArmorItem.Type type) {
        GEAR.put(id, ModItems.ITEMS.register(id, () -> new ArmorItem(Warden.INSTANCE, type, new Item.Properties().rarity(Rarity.UNCOMMON))));
    }

    /** Salt Iron Sword: creatures hit with it recoil (a short fright), except bosses and blockers. */
    static final class SaltSword extends SwordItem {
        SaltSword(Properties p) { super(Tiers.IRON, 3, -2.4f, p); }
        @Override public boolean hurtEnemy(ItemStack s, LivingEntity target, LivingEntity attacker) {
            if (target instanceof HorrorEntity h) h.scareFromLight(50);
            return super.hurtEnemy(s, target, attacker);
        }
    }

    /** Echo Dagger: fast; strikes from behind land for double damage. */
    static final class EchoDagger extends SwordItem {
        EchoDagger(Properties p) { super(Tiers.IRON, 1, -1.6f, p); }
        @Override public boolean hurtEnemy(ItemStack s, LivingEntity target, LivingEntity attacker) {
            net.minecraft.world.phys.Vec3 look = target.getLookAngle(), to = attacker.position().subtract(target.position()).normalize();
            if (look.x * to.x + look.z * to.z < -0.3 && target.level() instanceof net.minecraft.server.level.ServerLevel) target.hurt(attacker.damageSources().generic(), 2.0f);
            return super.hurtEnemy(s, target, attacker);
        }
    }

    /** Signal Halberd: heavy and slow; extra damage to signal-touched creatures (any HorrorEntity). */
    static final class SignalHalberd extends SwordItem {
        SignalHalberd(Properties p) { super(Tiers.DIAMOND, 4, -3.0f, p); }
        @Override public boolean hurtEnemy(ItemStack s, LivingEntity target, LivingEntity attacker) {
            if (target instanceof HorrorEntity) target.hurt(attacker.damageSources().generic(), 3.0f);
            return super.hurtEnemy(s, target, attacker);
        }
    }

    public static final String[] ARMOR_IDS = {"lantern_warden_helmet", "lantern_warden_chestplate", "lantern_warden_leggings", "lantern_warden_boots"};
    public static final String[] WEAPON_IDS = {"salt_iron_sword", "echo_dagger", "signal_halberd"};

    static {
        armor(ARMOR_IDS[0], ArmorItem.Type.HELMET);
        armor(ARMOR_IDS[1], ArmorItem.Type.CHESTPLATE);
        armor(ARMOR_IDS[2], ArmorItem.Type.LEGGINGS);
        armor(ARMOR_IDS[3], ArmorItem.Type.BOOTS);
        GEAR.put(WEAPON_IDS[0], ModItems.ITEMS.register(WEAPON_IDS[0], () -> new SaltSword(new Item.Properties())));
        GEAR.put(WEAPON_IDS[1], ModItems.ITEMS.register(WEAPON_IDS[1], () -> new EchoDagger(new Item.Properties())));
        GEAR.put(WEAPON_IDS[2], ModItems.ITEMS.register(WEAPON_IDS[2], () -> new SignalHalberd(new Item.Properties().rarity(Rarity.RARE))));
    }

    public static boolean fullSet(Player p) {
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack st = p.getItemBySlot(s);
            if (!(st.getItem() instanceof ArmorItem a) || a.getMaterial() != Warden.INSTANCE) return false;
        }
        return true;
    }

    public static void init() {}

    private GearItems() {}
}
