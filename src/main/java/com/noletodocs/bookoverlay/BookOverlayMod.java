package com.noletodocs.bookoverlay;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
            Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new StoryBookScreen()));
        }
    }

    public static class StoryBookScreen extends Screen {
        private static final ResourceLocation BOOK_LOCATION = new ResourceLocation("textures/gui/book.png");
        private final List<String> paginas = new ArrayList<>();
        private int paginaAtual = 0;

        public StoryBookScreen() {
            super(Component.literal("Livro de Histórias"));
            carregarHistoria();
        }

        private void carregarHistoria() {
            try {
                File mcFolder = Minecraft.getInstance().gameDirectory;
                File file = new File(mcFolder, "historia.txt");

                if (file.exists()) {
                    List<String> linhas = Files.readAllLines(file.toPath());
                    paginas.clear();
                    StringBuilder pageBuilder = new StringBuilder();
                    int lineCount = 0;

                    for (String linha : linhas) {
                        pageBuilder.append(linha).append("\n");
                        lineCount++;
                        if (lineCount >= 10) {
                            paginas.add(pageBuilder.toString());
                            pageBuilder = new StringBuilder();
                            lineCount = 0;
                        }
                    }
                    if (pageBuilder.length() > 0) {
                        paginas.add(pageBuilder.toString());
                    }
                } else {
                    paginas.add("Crie o arquivo historia.txt na pasta do jogo!");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (paginas.isEmpty()) {
                paginas.add("O arquivo historia.txt esta vazio.");
            }
        }

        @Override
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            this.renderBackground(guiGraphics);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            int guiWidth = 192;
            int guiHeight = 192;
            int startX = (this.width - guiWidth) / 2;
            int startY = (this.height - guiHeight) / 2;

            guiGraphics.blit(BOOK_LOCATION, startX, startY, 0, 0, guiWidth, guiHeight);

            // Renderizar texto na página esquerda e direita
            Font font = this.font;
            int textX = startX + 36;
            int textY = startY + 34;

            if (paginaAtual < paginas.size()) {
                String textoPagina = paginas.get(paginaAtual);
                String[] linhas = textoPagina.split("\n");
                int currentY = textY;
                for (String linha : linhas) {
                    guiGraphics.drawString(font, linha, textX, currentY, 0x000000, false);
                    currentY += 10;
                }
            }

            String paginaStr = "Página " + (paginaAtual + 1) + " de " + paginas.size();
            guiGraphics.drawCenteredString(font, paginaStr, startX + guiWidth / 2, startY + 158, 0x555555);

            super.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                if (paginaAtual < paginas.size() - 1) {
                    paginaAtual++;
                    return true;
                }
            } else if (keyCode == GLFW.GLFW_KEY_LEFT) {
                if (paginaAtual > 0) {
                    paginaAtual--;
                    return true;
                }
            } else if (keyCode == GLFW.GLFW_KEY_R) {
                carregarHistoria();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
