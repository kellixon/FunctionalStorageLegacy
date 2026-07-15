package com.xinyihl.functionalstoragelegacy.common.recipe;

import com.xinyihl.functionalstoragelegacy.Tags;
import com.xinyihl.functionalstoragelegacy.common.block.DrawerWoodType;
import com.xinyihl.functionalstoragelegacy.common.storage.DrawerLayout;
import com.xinyihl.functionalstoragelegacy.common.block.WoodDrawerBlock;
import com.xinyihl.functionalstoragelegacy.misc.RegistrationHandler;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import net.minecraftforge.registries.IForgeRegistry;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FunctionalStorageRecipes {

    private FunctionalStorageRecipes() {
    }

    public static void register(IForgeRegistry<IRecipe> registry) {
        Ingredient emptyDrawer = new EmptyDrawerIngredient(buildDrawerStacks());
        Ingredient drawerlessWood = new DrawerlessWoodIngredient(buildDrawerlessWoodStacks());

        registerWoodDrawerRecipes(registry, drawerlessWood);
        registerBlockRecipes(registry, emptyDrawer);
        registerItemRecipes(registry, emptyDrawer);
    }

    private static void registerWoodDrawerRecipes(IForgeRegistry<IRecipe> registry, Ingredient drawerlessWood) {
        for (WoodDrawerBlock block : RegistrationHandler.WOOD_DRAWER_BLOCKS) {
            Object plankIngredient = getWoodIngredient(block.getWoodType(), drawerlessWood);
            ItemStack output = new ItemStack(block, block.getDrawerLayout().getSlotCount());
            String name = block.getRegistryName().getPath();

            if (block.getDrawerLayout() == DrawerLayout.X_1) {
                registerShaped(registry, name, output,
                        "PPP", "PCP", "PPP",
                        'P', plankIngredient,
                        'C', "chestWood");
            }
            if (block.getDrawerLayout() == DrawerLayout.X_2) {
                registerShaped(registry, name, output,
                        "PCP", "PPP", "PCP",
                        'P', plankIngredient,
                        'C', "chestWood");
            }
            if (block.getDrawerLayout() == DrawerLayout.X_4) {
                registerShaped(registry, name, output,
                        "CPC", "PPP", "CPC",
                        'P', plankIngredient,
                        'C', "chestWood");
            }
        }
    }

    private static void registerBlockRecipes(IForgeRegistry<IRecipe> registry, Ingredient emptyDrawer) {
        registerShaped(registry, "storage_controller", new ItemStack(RegistrationHandler.DRAWER_CONTROLLER_BLOCK),
                "IBI", "CDC", "IBI",
                'I', "stone",
                'B', Blocks.QUARTZ_BLOCK,
                'C', emptyDrawer,
                'D', Items.COMPARATOR);

        registerShaped(registry, "controller_extension", new ItemStack(RegistrationHandler.CONTROLLER_EXTENSION_BLOCK),
                "IBI", "CDC", "IBI",
                'I', "stone",
                'B', Blocks.QUARTZ_BLOCK,
                'C', emptyDrawer,
                'D', Items.REPEATER);

        registerShaped(registry, "compacting_drawer", new ItemStack(RegistrationHandler.COMPACTING_DRAWER_BLOCK),
                "SSS", "PDP", "SIS",
                'S', "stone",
                'P', Blocks.PISTON,
                'D', emptyDrawer,
                'I', "ingotIron");

        registerShaped(registry, "simple_compacting_drawer", new ItemStack(RegistrationHandler.SIMPLE_COMPACTING_DRAWER_BLOCK),
                "SSS", "SDP", "SIS",
                'S', "stone",
                'P', Blocks.PISTON,
                'D', emptyDrawer,
                'I', "ingotIron");

        registerShaped(registry, "fluid_1", new ItemStack(RegistrationHandler.FLUID_DRAWER_1),
                "PPP", "PCP", "PPP",
                'P', "plankWood",
                'C', Items.BUCKET);

        registerShaped(registry, "fluid_2", new ItemStack(RegistrationHandler.FLUID_DRAWER_2, 2),
                "PCP", "PPP", "PCP",
                'P', "plankWood",
                'C', Items.BUCKET);

        registerShaped(registry, "fluid_4", new ItemStack(RegistrationHandler.FLUID_DRAWER_4, 4),
                "CPC", "PPP", "CPC",
                'P', "plankWood",
                'C', Items.BUCKET);

        registerShaped(registry, "ender_drawer", new ItemStack(RegistrationHandler.ENDER_DRAWER_BLOCK),
                "PPP", "LCL", "PPP",
                'P', "plankWood",
                'L', emptyDrawer,
                'C', Blocks.ENDER_CHEST);

        registerShaped(registry, "armory_cabinet", new ItemStack(RegistrationHandler.ARMORY_CABINET_BLOCK),
                "ICI", "CDC", "IBI",
                'I', "stone",
                'C', emptyDrawer,
                'D', Items.COMPARATOR,
                'B', "ingotNetherite");
    }

    private static void registerItemRecipes(IForgeRegistry<IRecipe> registry, Ingredient emptyDrawer) {
        registerShaped(registry, "iron_downgrade", new ItemStack(RegistrationHandler.IRON_DOWNGRADE),
                "III", "IDI", "III",
                'I', "ingotIron",
                'D', emptyDrawer);

        registerShaped(registry, "void_upgrade", new ItemStack(RegistrationHandler.VOID_UPGRADE),
                "III", "IDI", "III",
                'I', "obsidian",
                'D', emptyDrawer);

        registerShaped(registry, "configuration_tool", new ItemStack(RegistrationHandler.CONFIGURATION_TOOL),
                "PPG", "PDG", "PEP",
                'P', Items.PAPER,
                'G', "ingotGold",
                'D', emptyDrawer,
                'E', Items.EMERALD);

        registerShaped(registry, "linking_tool", new ItemStack(RegistrationHandler.LINKING_TOOL),
                "PPG", "PDG", "PEP",
                'P', Items.PAPER,
                'G', "ingotGold",
                'D', emptyDrawer,
                'E', Items.DIAMOND);

        registerShaped(registry, "copper_upgrade", new ItemStack(RegistrationHandler.COPPER_UPGRADE),
                "IBI", "CDC", "IBI",
                'I', "ingotCopper",
                'B', "blockCopper",
                'C', "chestWood",
                'D', emptyDrawer);

        registerShaped(registry, "gold_upgrade", new ItemStack(RegistrationHandler.GOLD_UPGRADE),
                "IBI", "CDC", "BIB",
                'I', "ingotGold",
                'B', "blockGold",
                'C', "chestWood",
                'D', RegistrationHandler.COPPER_UPGRADE);

        registerShaped(registry, "diamond_upgrade", new ItemStack(RegistrationHandler.DIAMOND_UPGRADE),
                "IBI", "CDC", "IBI",
                'I', "gemDiamond",
                'B', "blockDiamond",
                'C', "chestWood",
                'D', RegistrationHandler.GOLD_UPGRADE);

        registerShaped(registry, "netherite_upgrade", new ItemStack(RegistrationHandler.NETHERITE_UPGRADE),
                "IBI", "CDC", "IBI",
                'I', "ingotNetherite",
                'B', "blockNetherite",
                'C', "chestWood",
                'D', RegistrationHandler.DIAMOND_UPGRADE);

        registerShaped(registry, "max_storage_upgrade", new ItemStack(RegistrationHandler.MAX_STORAGE_UPGRADE),
            "SNS", "NUN", "SNS",
            'S', Items.NETHER_STAR,
            'N', "ingotNetherite",
            'U', RegistrationHandler.NETHERITE_UPGRADE);

        registerShaped(registry, "redstone_upgrade", new ItemStack(RegistrationHandler.REDSTONE_UPGRADE),
                "IBI", "CDC", "IBI",
                'I', Items.REDSTONE,
                'B', Blocks.REDSTONE_BLOCK,
                'C', Items.COMPARATOR,
                'D', emptyDrawer);

        registerShaped(registry, "pulling_upgrade", new ItemStack(RegistrationHandler.PULLING_UPGRADE),
                "ICI", "IDI", "IBI",
                'I', "stone",
                'C', Blocks.HOPPER,
                'D', emptyDrawer,
                'B', Items.REDSTONE);

        registerShaped(registry, "pushing_upgrade", new ItemStack(RegistrationHandler.PUSHING_UPGRADE),
                "IBI", "IDI", "IRI",
                'I', "stone",
                'B', Items.REDSTONE,
                'D', emptyDrawer,
                'R', Blocks.HOPPER);

        registerShaped(registry, "collector_upgrade", new ItemStack(RegistrationHandler.COLLECTOR_UPGRADE),
                "IBI", "RDR", "IBI",
                'I', "stone",
                'B', Blocks.HOPPER,
                'R', Items.REDSTONE,
                'D', emptyDrawer);

        registerShaped(registry, "ore_dictionary_upgrade", new ItemStack(RegistrationHandler.ORE_DICTIONARY_UPGRADE),
            "RBR", "QDQ", "RER",
            'R', Items.REDSTONE,
            'B', Items.BOOK,
            'Q', Items.QUARTZ,
            'D', emptyDrawer,
            'E', Items.EMERALD);

        registerShaped(registry, "wireless_pulling_upgrade", new ItemStack(RegistrationHandler.WIRELESS_PULLING_UPGRADE),
                "EPE", "PUP", "EPE",
                'E', Items.ENDER_PEARL,
                'P', Items.REDSTONE,
                'U', RegistrationHandler.PULLING_UPGRADE);

        registerShaped(registry, "wireless_pushing_upgrade", new ItemStack(RegistrationHandler.WIRELESS_PUSHING_UPGRADE),
                "EPE", "PUP", "EPE",
                'E', Items.ENDER_PEARL,
                'P', Items.REDSTONE,
                'U', RegistrationHandler.PUSHING_UPGRADE);
    }

    private static Object getWoodIngredient(DrawerWoodType woodType, Ingredient drawerlessWood) {
        if (woodType == DrawerWoodType.OAK) {
            return drawerlessWood;
        }
        return new ItemStack(woodType.getPlanks(), 1, woodType.getMeta());
    }

    private static ItemStack[] buildDrawerStacks() {
        List<ItemStack> stacks = new ArrayList<>();
        for (WoodDrawerBlock block : RegistrationHandler.WOOD_DRAWER_BLOCKS) {
            stacks.add(new ItemStack(block));
        }
        stacks.add(new ItemStack(RegistrationHandler.COMPACTING_DRAWER_BLOCK));
        stacks.add(new ItemStack(RegistrationHandler.SIMPLE_COMPACTING_DRAWER_BLOCK));
        return stacks.toArray(new ItemStack[0]);
    }

    private static ItemStack[] buildDrawerlessWoodStacks() {
        Map<String, ItemStack> stacks = new LinkedHashMap<>();
        addStack(stacks, new ItemStack(Blocks.PLANKS, 1, 0));
        for (ItemStack stack : OreDictionary.getOres("plankWood")) {
            if (stack.isEmpty()) {
                continue;
            }
            ResourceLocation registryName = stack.getItem().getRegistryName();
            if (registryName == null) {
                continue;
            }
            if ("minecraft".equals(registryName.getNamespace())) {
                if (stack.getItem() != Item.getItemFromBlock(Blocks.PLANKS) || stack.getMetadata() != 0) {
                    continue;
                }
            }
            addStack(stacks, stack);
        }
        return stacks.values().toArray(new ItemStack[0]);
    }

    private static void addStack(Map<String, ItemStack> stacks, ItemStack stack) {
        ResourceLocation registryName = stack.getItem().getRegistryName();
        if (registryName == null) {
            return;
        }
        String key = registryName + "@" + stack.getMetadata();
        stacks.putIfAbsent(key, stack.copy());
    }

    private static void registerShaped(IForgeRegistry<IRecipe> registry, String name, ItemStack result, Object... recipe) {
        ShapedOreRecipe shapedRecipe = new ShapedOreRecipe(null, result, recipe);
        shapedRecipe.setRegistryName(new ResourceLocation(Tags.MOD_ID, name));
        registry.register(shapedRecipe);
    }

    private static void registerShapeless(IForgeRegistry<IRecipe> registry, String name, ItemStack result, Object... recipe) {
        ShapelessOreRecipe shapelessRecipe = new ShapelessOreRecipe(null, result, recipe);
        shapelessRecipe.setRegistryName(new ResourceLocation(Tags.MOD_ID, name));
        registry.register(shapelessRecipe);
    }

    private static final class EmptyDrawerIngredient extends Ingredient {

        private EmptyDrawerIngredient(ItemStack[] stacks) {
            super(stacks);
        }

        @Override
        public boolean apply(@Nullable ItemStack input) {
            return input != null && !input.isEmpty() && !input.hasTagCompound() && super.apply(input);
        }

        @Override
        public boolean isSimple() {
            return false;
        }
    }

    private static final class DrawerlessWoodIngredient extends Ingredient {

        private DrawerlessWoodIngredient(ItemStack[] stacks) {
            super(stacks);
        }
    }
}
