package com.noletodocs.bookoverlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@Mod("examplemod")
public class BookOverlayMod {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, "examplemod");

    public static final RegistryObject<Item> STORY_BOOK = ITEMS.register("story_book", () -> new Item(new Item.Properties().stacksTo(1)) {
        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack itemstack = player.getItemInHand(hand);
            if (level.isClientSide()) {
                openBookScreen();
            }
            return InteractionResultHolder.sidedSuccess(itemstack, level.isClientSide());
        }
    });

    public BookOverlayMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modEventBus);
    }

    public static void openBookScreen() {
        List<Component> pages = new ArrayList<>();
        try {
            File mcFolder = Minecraft.getInstance().gameDirectory;
            File file = new File(mcFolder, "historia.txt");

            if (file.exists()) {
                List<String> linhas = Files.readAllLines(file.toPath());
                StringBuilder pageBuilder = new StringBuilder();
                int lineCount = 0;

                for (String linha : linhas) {
                    pageBuilder.append(linha).append("\n");
                    lineCount++;
                    if (lineCount >= 12) {
                        pages.add(Component.literal(pageBuilder.toString()));
                        pageBuilder = new StringBuilder();
                        lineCount = 0;
                    }
                }
                if (pageBuilder.length() > 0) {
                    pages.add(Component.literal(pageBuilder.toString()));
                }
            } else {
                pages.add(Component.literal("Crie o arquivo historia.txt na pasta raiz do .minecraft!"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (pages.isEmpty()) {
            pages.add(Component.literal("O arquivo historia.txt esta vazio."));
        }

        Minecraft.getInstance().setScreen(new BookViewScreen(new BookViewScreen.BookAccess() {
            @Override
            public int getPageCount() {
                return pages.size();
            }

            @Override
            public Component getPage(int index) {
                return index >= 0 && index < pages.size() ? pages.get(index) : Component.empty();
            }
        }));
    }
}
