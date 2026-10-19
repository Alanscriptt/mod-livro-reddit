package com.noletodocs.bookoverlay;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@Mod("examplemod")
public class BookOverlayMod {

    private static final ResourceLocation BOOK_TEXTURE = new ResourceLocation("textures/entity/enchanting_table_book.png");

    private final List<List<FormattedCharSequence>> paginas = new ArrayList<>();
    private int spreadAtual = 0;
    private boolean livroAtivo = true;
    private boolean historiaCarregada = false;

    // Animação limpa de virar a folha 3D (de 0.0 a 1.0)
    private float animProgresso = 0.0F;
    private float animProgressoAnt = 0.0F;
    private int direcaoVirada = 0; // 1 = avançando (dir -> esq), -1 = voltando (esq -> dir)

    public BookOverlayMod() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            MinecraftForge.EVENT_BUS.register(this);
        }
    }

    private void carregarHistoria() {
        paginas.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null) return;

        try {
            File file = new File(mc.gameDirectory, "historia.txt");
            String textoCompleto;

            if (file.exists()) {
                textoCompleto = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            } else {
                textoCompleto = "Crie o arquivo historia.txt na pasta .minecraft!\n\nUse as SETAS para virar as páginas e a tecla R para recarregar o texto.";
            }

            if (textoCompleto.trim().isEmpty()) {
                textoCompleto = "O arquivo historia.txt está vazio.";
            }

            List<FormattedCharSequence> todasLinhas = new ArrayList<>();
            String[] paragrafos = textoCompleto.replace("\r", "").split("\n");
            for (String p : paragrafos) {
                if (p.trim().isEmpty()) {
                    todasLinhas.add(FormattedCharSequence.EMPTY);
                } else {
                    todasLinhas.addAll(mc.font.split(Component.literal(p), 86));
                }
            }

            int linhasPorPagina = 11;
            for (int i = 0; i < todasLinhas.size(); i += linhasPorPagina) {
                paginas.add(new ArrayList<>(todasLinhas.subList(i, Math.min(i + linhasPorPagina, todasLinhas.size()))));
            }

            if (paginas.isEmpty()) {
                paginas.add(mc.font.split(Component.literal("Sem texto."), 86));
            }
            historiaCarregada = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        if (!historiaCarregada) {
            carregarHistoria();
        }

        animProgressoAnt = animProgresso;
        if (direcaoVirada != 0) {
            animProgresso += 0.18F; // Velocidade da folha virando
            if (animProgresso >= 1.0F) {
                animProgresso = 0.0F;
                animProgressoAnt = 0.0F;
                direcaoVirada = 0;
            }
        }
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        if (event.getAction() == GLFW.GLFW_PRESS) {
            if (event.getKey() == GLFW.GLFW_KEY_B) {
                livroAtivo = !livroAtivo;
                if (livroAtivo) carregarHistoria();
            } else if (event.getKey() == GLFW.GLFW_KEY_RIGHT) {
                if ((spreadAtual + 1) * 2 < paginas.size()) {
                    spreadAtual++;
                    direcaoVirada = 1;
                    animProgresso = 0.01F;
                    animProgressoAnt = 0.01F;
                    mc.player.playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 1.0F);
                }
            } else if (event.getKey() == GLFW.GLFW_KEY_LEFT) {
                if (spreadAtual > 0) {
                    spreadAtual--;
                    direcaoVirada = -1;
                    animProgresso = 0.01F;
                    animProgressoAnt = 0.01F;
                    mc.player.playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.9F);
                }
            } else if (event.getKey() == GLFW.GLFW_KEY_R) {
                carregarHistoria();
                spreadAtual = 0;
                mc.player.displayClientMessage(Component.literal("§a[Relatos] historia.txt recarregado!"), true);
            }
        }
    }

    private boolean isBookItem(ItemStack stack) {
        return stack.is(Items.BOOK) || stack.is(Items.WRITABLE_BOOK) || stack.is(Items.WRITTEN_BOOK) || stack.is(Items.ENCHANTED_BOOK);
    }

    @SubscribeEvent
    public void onRenderHand(RenderHandEvent event) {
        if (!livroAtivo) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        boolean mainHasBook = isBookItem(mc.player.getMainHandItem());
        boolean offHasBook = isBookItem(mc.player.getOffhandItem());

        if (mainHasBook || offHasBook) {
            if (event.getHand() == InteractionHand.MAIN_HAND && !mainHasBook) return;
            if (event.getHand() == InteractionHand.OFF_HAND && (!offHasBook || mainHasBook)) return;
        } else {
            if (event.getHand() != InteractionHand.OFF_HAND) return;
        }

        event.setCanceled(true);

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        int light = LightTexture.FULL_BRIGHT;
        float partialTick = event.getPartialTick();

        poseStack.pushPose();

        boolean isRightHand = (event.getHand() == InteractionHand.MAIN_HAND)
                ? (mc.options.mainHand().get() == HumanoidArm.RIGHT)
                : (mc.options.mainHand().get() == HumanoidArm.LEFT);
        float side = isRightHand ? 1.0F : -1.0F;

        float bob = Mth.sin((mc.player.tickCount + partialTick) * 0.1F) * 0.006F;

        // Posiciona o livro na mão de forma que apareça 100% de frente para os olhos do jogador!
        poseStack.translate(side * 0.34F, -0.20F + bob - (event.getEquipProgress() * 0.2F), -0.60F);

        // Leve inclinação natural para olhar diretamente para o rosto do jogador (igual à 1ª foto!)
        poseStack.mulPose(Axis.YP.rotationDegrees(side * -16.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(10.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * 6.0F));

        // 1) Desenha o braço do jogador segurando o livro por baixo
        renderizarBraco(poseStack, buffer, event.getPackedLight(), isRightHand, side);

        // 2) Desenha o Livro 3D (Capa + Blocos de páginas + Texto colado na página + Folha animada)
        Font font = mc.font;
        int pagEsqIndex = spreadAtual * 2;
        int pagDirIndex = spreadAtual * 2 + 1;

        // --- LADO ESQUERDO DO LIVRO (Capa + Página grossa + Texto na mesma peça!) ---
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(14.0F)); // Leve curvatura 3D de livro aberto

        VertexConsumer bookConsumer = buffer.getBuffer(RenderType.entitySolid(BOOK_TEXTURE));
        // Capa esquerda (Marrom/Dourada)
        desenharCaixa3D(poseStack, bookConsumer, -0.255F, -0.185F, -0.022F, 0.255F, 0.185F, -0.008F, 0, 0, 16, 20, light);
        // Bloco de páginas esquerdo (Cor de papel creme)
        desenharCaixa3D(poseStack, bookConsumer, -0.238F, -0.168F, -0.008F, -0.004F, 0.168F, 0.018F, 12, 10, 24, 19, light);
        buffer.endBatch(RenderType.entitySolid(BOOK_TEXTURE));

        // Texto da página esquerda (Colado a 0.001F acima da superfície do papel: Z = 0.019F!)
        if (pagEsqIndex < paginas.size()) {
            poseStack.pushPose();
            poseStack.translate(-0.226F, 0.150F, 0.0195F);
            poseStack.scale(0.00205F, -0.00205F, 0.00205F);
            desenharLinhasDaPagina(font, paginas.get(pagEsqIndex), poseStack.last().pose(), buffer, light, pagEsqIndex + 1);
            poseStack.popPose();
        }
        poseStack.popPose();

        // --- LADO DIREITO DO LIVRO (Capa + Página grossa + Texto na mesma peça!) ---
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-14.0F)); // Leve curvatura 3D de livro aberto

        bookConsumer = buffer.getBuffer(RenderType.entitySolid(BOOK_TEXTURE));
        // Capa direita (Marrom/Dourada)
        desenharCaixa3D(poseStack, bookConsumer, 0.0F, -0.185F, -0.022F, 0.255F, 0.185F, -0.008F, 0, 0, 16, 20, light);
        // Bloco de páginas direito (Cor de papel creme)
        desenharCaixa3D(poseStack, bookConsumer, 0.004F, -0.168F, -0.008F, 0.238F, 0.168F, 0.018F, 12, 10, 24, 19, light);
        buffer.endBatch(RenderType.entitySolid(BOOK_TEXTURE));

        // Texto da página direita (Colado a 0.001F acima da superfície do papel: Z = 0.019F!)
        if (pagDirIndex < paginas.size()) {
            poseStack.pushPose();
            poseStack.translate(0.018F, 0.150F, 0.0195F);
            poseStack.scale(0.00205F, -0.00205F, 0.00205F);
            desenharLinhasDaPagina(font, paginas.get(pagDirIndex), poseStack.last().pose(), buffer, light, pagDirIndex + 1);
            poseStack.popPose();
        }
        poseStack.popPose();

        // --- FOLHA ANIMADA VIRANDO (Só aparece quando você aperta a seta!) ---
        if (direcaoVirada != 0) {
            float prog = Mth.lerp(partialTick, animProgressoAnt, animProgresso);
            float anguloFolha = (direcaoVirada == 1)
                    ? Mth.lerp(prog, -14.0F, 166.0F)
                    : Mth.lerp(prog, -166.0F, 14.0F);

            poseStack.pushPose();
            poseStack.translate(0.0F, 0.0F, 0.020F);
            poseStack.mulPose(Axis.YP.rotationDegrees(anguloFolha));
            bookConsumer = buffer.getBuffer(RenderType.entitySolid(BOOK_TEXTURE));
            desenharCaixa3D(poseStack, bookConsumer, 0.002F, -0.166F, -0.002F, 0.234F, 0.166F, 0.002F, 12, 10, 24, 19, light);
            buffer.endBatch(RenderType.entitySolid(BOOK_TEXTURE));
            poseStack.popPose();
        }

        buffer.endBatch();
        poseStack.popPose();
    }

    private void renderizarBraco(PoseStack poseStack, MultiBufferSource buffer, int packedLight, boolean isRightHand, float side) {
        try {
            Minecraft mc = Minecraft.getInstance();
            AbstractClientPlayer player = mc.player;
            if (player == null) return;

            EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
            PlayerRenderer playerRenderer = (PlayerRenderer) dispatcher.getRenderer(player);

            poseStack.pushPose();
            poseStack.translate(side * -0.02F, -0.38F, 0.08F);
            poseStack.mulPose(Axis.XP.rotationDegrees(-75.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(side * 10.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(side * -8.0F));
            poseStack.scale(0.75F, 0.75F, 0.75F);

            if (isRightHand) {
                playerRenderer.renderRightHand(poseStack, buffer, packedLight, player);
            } else {
                playerRenderer.renderLeftHand(poseStack, buffer, packedLight, player);
            }
            poseStack.popPose();
        } catch (Exception ignored) {
        }
    }

    private void desenharLinhasDaPagina(Font font, List<FormattedCharSequence> linhas, Matrix4f matrix, MultiBufferSource buffer, int light, int numPagina) {
        int y = 0;
        for (FormattedCharSequence linha : linhas) {
            font.drawInBatch(linha, 0, y, 0x261C14, false, matrix, buffer, Font.DisplayMode.NORMAL, 0, light);
            y += 10;
        }
        String rodape = "- " + numPagina + " -";
        int larguraRodape = font.width(rodape);
        font.drawInBatch(rodape, (86 - larguraRodape) / 2.0F, 114, 0x6E5A47, false, matrix, buffer, Font.DisplayMode.NORMAL, 0, light);
    }

    // Constrói os blocos 3D do livro (Capa e Páginas) orientados diretamente para a visão do jogador
    private void desenharCaixa3D(PoseStack poseStack, VertexConsumer consumer,
                                 float x1, float y1, float z1, float x2, float y2, float z2,
                                 int u1, int v1, int u2, int v2, int light) {
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();

        float minU = u1 / 64.0F;
        float maxU = u2 / 64.0F;
        float minV = v1 / 32.0F;
        float maxV = v2 / 32.0F;

        // Frente (Onde fica o texto - virado para o jogador, Z = z2)
        vertice(consumer, pose, normal, x1, y1, z2, minU, maxV, 0, 0, 1, light);
        vertice(consumer, pose, normal, x2, y1, z2, maxU, maxV, 0, 0, 1, light);
        vertice(consumer, pose, normal, x2, y2, z2, maxU, minV, 0, 0, 1, light);
        vertice(consumer, pose, normal, x1, y2, z2, minU, minV, 0, 0, 1, light);

        // Trás (Z = z1)
        vertice(consumer, pose, normal, x2, y1, z1, minU, maxV, 0, 0, -1, light);
        vertice(consumer, pose, normal, x1, y1, z1, maxU, maxV, 0, 0, -1, light);
        vertice(consumer, pose, normal, x1, y2, z1, maxU, minV, 0, 0, -1, light);
        vertice(consumer, pose, normal, x2, y2, z1, minU, minV, 0, 0, -1, light);

        // Topo (Y = y2)
        vertice(consumer, pose, normal, x1, y2, z2, minU, maxV, 0, 1, 0, light);
        vertice(consumer, pose, normal, x2, y2, z2, maxU, maxV, 0, 1, 0, light);
        vertice(consumer, pose, normal, x2, y2, z1, maxU, minV, 0, 1, 0, light);
        vertice(consumer, pose, normal, x1, y2, z1, minU, minV, 0, 1, 0, light);

        // Base (Y = y1)
        vertice(consumer, pose, normal, x1, y1, z1, minU, maxV, 0, -1, 0, light);
        vertice(consumer, pose, normal, x2, y1, z1, maxU, maxV, 0, -1, 0, light);
        vertice(consumer, pose, normal, x2, y1, z2, maxU, minV, 0, -1, 0, light);
        vertice(consumer, pose, normal, x1, y1, z2, minU, minV, 0, -1, 0, light);

        // Esquerda (X = x1)
        vertice(consumer, pose, normal, x1, y1, z1, minU, maxV, -1, 0, 0, light);
        vertice(consumer, pose, normal, x1, y1, z2, maxU, maxV, -1, 0, 0, light);
        vertice(consumer, pose, normal, x1, y2, z2, maxU, minV, -1, 0, 0, light);
        vertice(consumer, pose, normal, x1, y2, z1, minU, minV, -1, 0, 0, light);

        // Direita (X = x2)
        vertice(consumer, pose, normal, x2, y1, z2, minU, maxV, 1, 0, 0, light);
        vertice(consumer, pose, normal, x2, y1, z1, maxU, maxV, 1, 0, 0, light);
        vertice(consumer, pose, normal, x2, y2, z1, maxU, minV, 1, 0, 0, light);
        vertice(consumer, pose, normal, x2, y2, z2, minU, minV, 1, 0, 0, light);
    }

    private void vertice(VertexConsumer consumer, Matrix4f pose, Matrix3f normal,
                         float x, float y, float z, float u, float v,
                         float nx, float ny, float nz, int light) {
        consumer.vertex(pose, x, y, z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(normal, nx, ny, nz)
                .endVertex();
    }
}
