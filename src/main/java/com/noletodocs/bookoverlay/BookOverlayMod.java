package com.noletodocs.bookoverlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@Mod("examplemod")
public class BookOverlayMod {

    public BookOverlayMod() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            MinecraftForge.EVENT_BUS.register(this);
        }
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.Key event) {
        if (event.getAction() == GLFW.GLFW_PRESS && event.getKey() == GLFW.GLFW_KEY_B) {
            Minecraft.getInstance().execute(BookOverlayMod::abrirLivroDoJogo);
        }
    }

    public static void abrirLivroDoJogo() {
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
                    if (lineCount >= 14) {
                        pages.add(Component.literal(pageBuilder.toString()));
                        pageBuilder = new StringBuilder();
                        lineCount = 0;
                    }
                }
                if (pageBuilder.length() > 0) {
                    pages.add(Component.literal(pageBuilder.toString()));
                }
            } else {
                pages.add(Component.literal("Crie o arquivo historia.txt na pasta do jogo!"));
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
