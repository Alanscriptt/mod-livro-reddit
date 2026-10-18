package com.noletodocs.bookoverlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@Mod("bookoverlay")
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = "bookoverlay")
public class BookOverlayMod {

    private static int paginaAtual = 0;
    private static List<String> paginas = new ArrayList<>();
    private static boolean carregado = false;

    public static void carregarHistoria() {
        try {
            File file = new File(Minecraft.getInstance().gameDirectory, "historia.txt");
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
                Files.writeString(file.toPath(), "Crie o arquivo historia.txt na pasta .minecraft para colocar seus relatos!");
                paginas.add("Arquivo historia.txt nao encontrado.\nCrie o arquivo na pasta .minecraft.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        carregado = true;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiOverlayEvent.Post event) {
        if (!carregado) carregarHistoria();
        if (paginas.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        GuiGraphics guiGraphics = event.getGuiGraphics();
        Font font = mc.font;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int x = screenWidth - 180;
        int y = screenHeight - 160;

        String textoPagina = paginas.get(Math.min(paginaAtual, paginas.size() - 1));
        String[] linhas = textoPagina.split("\n");

        int currentY = y;
        for (String linha : linhas) {
            guiGraphics.drawString(font, linha, x, currentY, 0xFFFFFF, true);
            currentY += 10;
        }

        String infoPagina = "Pagina " + (paginaAtual + 1) + " / " + paginas.size();
        guiGraphics.drawString(font, infoPagina, x, y - 12, 0xFFFF00, true);
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
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
