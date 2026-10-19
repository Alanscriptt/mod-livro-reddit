package com.noletodocs.bookoverlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@Mod("examplemod")
public class BookOverlayMod {

    private static int paginaAtual = 0;
    private static List<String> paginas = new ArrayList<>();
    private static boolean carregado = false;

    public BookOverlayMod() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    public static void carregarHistoria() {
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
                    if (lineCount >= 8) {
                        paginas.add(pageBuilder.toString());
                        pageBuilder = new StringBuilder();
                        lineCount = 0;
                    }
                }
                if (pageBuilder.length() > 0) {
                    paginas.add(pageBuilder.toString());
                }
            } else {
                paginas.clear();
                paginas.add("Crie o arquivo historia.txt na pasta do jogo!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        carregado = true;
    }

    @SubscribeEvent
    public void onRenderGui(RenderGuiOverlayEvent.Post event) {
        if (!carregado) carregarHistoria();
        if (paginas.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        GuiGraphics guiGraphics = event.getGuiGraphics();
        Font font = mc.font;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // Posicionamento no canto superior esquerdo para nao cobrir o mini-mapa
        int x = 10;
        int y = 10;

        String textoPagina = paginas.get(Math.min(paginaAtual, paginas.size() - 1));
        String[] linhas = textoPagina.split("\n");

        String infoPagina = "=== PAGINA " + (paginaAtual + 1) + " / " + paginas.size() + " ===";
        guiGraphics.drawString(font, infoPagina, x, y, 0xFFFF00, true);

        int currentY = y + 14;
        for (String linha : linhas) {
            guiGraphics.drawString(font, linha, x, currentY, 0xFFFFFF, true);
            currentY += 10;
        }
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.Key event) {
        if (event.getAction() == GLFW.GLFW_PRESS) {
            if (event.getKey() == GLFW.GLFW_KEY_RIGHT) {
                if (paginaAtual < paginas.size() - 1) paginaAtual++;
            } else if (event.getKey() == GLFW.GLFW_KEY_LEFT) {
                if (paginaAtual > 0) paginaAtual--;
            } else if (event.getKey() == GLFW.GLFW_KEY_R) {
                carregarHistoria();
            }
        }
    }
}
