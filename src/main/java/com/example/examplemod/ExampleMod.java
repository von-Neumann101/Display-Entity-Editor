package com.example.examplemod;

import com.example.examplemod.item.DisplayEditorItem;
import com.example.examplemod.network.ModNetwork;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(ExampleMod.MODID)
public class ExampleMod {
    public static final String MODID = "display_entity_editor";
    public static final int MAX_SELECTION_GROUPS = 64;
    public static final ForgeConfigSpec.IntValue SELECTION_GROUP_COUNT;
    private static final ForgeConfigSpec CLIENT_CONFIG;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        SELECTION_GROUP_COUNT = builder.comment("Number of display selection groups")
                .defineInRange("selectionGroupCount", 9, 1, MAX_SELECTION_GROUPS);
        CLIENT_CONFIG = builder.build();
    }

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final RegistryObject<Item> DISPLAY_EDITOR = ITEMS.register("display_entity_editor",
            () -> new DisplayEditorItem(new Item.Properties().stacksTo(1)));

    public ExampleMod(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        context.registerConfig(ModConfig.Type.CLIENT, CLIENT_CONFIG);
        ITEMS.register(modBus);
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::addCreativeTabContents);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
    }

    private void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(DISPLAY_EDITOR);
        }
    }
}
