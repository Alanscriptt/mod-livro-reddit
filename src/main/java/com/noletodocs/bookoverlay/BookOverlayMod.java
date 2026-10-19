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

    // Largura ajustada para permitir letras maiores e mais legíveis
    private static final int LARGURA_TEXTO = 88;

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
                    todasLinhas.addAll(mc.font.split(Component.literal(p), LARGURA_TEXTO));
                }
            }

            int linhasPorPagina = 10;
            for (int i = 0; i < todasLinhas.size(); i += linhasPorPagina) {
                paginas.add(new ArrayList<>(todasLinhas.subList(i, Math.min(i + linhasPorPagina, todasLinhas.size()))));
            }

            if (paginas.isEmpty()) {
                paginas.add(mc.font.split(Component.literal("Sem texto."), LARGURA_TEXTO));
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
            animProgresso += 0.16F;
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

        // TRAVA 100% O BALANÇO DE CAMINHADA (View Bobbing) E DE CÂMERA APENAS NO LIVRO!
        // Ao resetar a matriz para a identidade, o livro ignora o chacoalhar dos passos do boneco.
        poseStack.last().pose().identity();
        poseStack.last().normal().identity();

        boolean isRightHand = (event.getHand() == InteractionHand.MAIN_HAND)
                ? (mc.options.mainHand().get() == HumanoidArm.RIGHT)
                : (mc.options.mainHand().get() == HumanoidArm.LEFT);
        float side = isRightHand ? 1.0F : -1.0F;

        // Posiciona o livro um pouquinho mais perto da câmera (-0.58F) para dar mais nitidez às letras
        poseStack.translate(side * 0.31F, -0.14F, -0.58F);

        // Mantém o ângulo de leitura aprovado
        poseStack.mulPose(Axis.YP.rotationDegrees(side * -18.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(-14.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * 8.0F));

        // 1) Desenha a Mão/Braço 3D segurando o livro POR TRÁS/BAIXO da capa
        renderizarBracoSegurandoLivro(poseStack, buffer, light, mc.player, side);

        // 2) Desenha o Livro 3D + Texto com Alto Contraste
        Font font = mc.font;
        int pagEsqIndex = spreadAtual * 2;
        int pagDirIndex = spreadAtual * 2 + 1;

        // --- LADO ESQUERDO DO LIVRO ---
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(13.0F));

        VertexConsumer bookConsumer = buffer.getBuffer(RenderType.entitySolid(BOOK_TEXTURE));
        desenharCaixa3D(poseStack, bookConsumer, -0.255F, -0.185F, -0.022F, 0.0F, 0.185F, -0.008F, 0, 0, 16, 20, 64, 32, light);
        desenharCaixa3D(poseStack, bookConsumer, -0.238F, -0.168F, -0.008F, -0.004F, 0.168F, 0.018F, 12, 10, 24, 19, 64, 32, light);
        buffer.endBatch(RenderType.entitySolid(BOOK_TEXTURE));

        if (pagEsqIndex < paginas.size()) {
            poseStack.pushPose();
            poseStack.translate(-0.121F, 0.0F, 0.0195F);
            // Escala levemente maior (0.00215F) para aumentar a legibilidade
            poseStack.scale(0.00215F, -0.00215F, 0.00215F);
            desenharLinhasComDestaque(font, paginas.get(pagEsqIndex), poseStack.last().pose(), buffer, light, pagEsqIndex + 1);
            poseStack.popPose();
        }
        poseStack.popPose();

        // --- LADO DIREITO DO LIVRO ---
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-13.0F));

        bookConsumer = buffer.getBuffer(RenderType.entitySolid(BOOK_TEXTURE));
        desenharCaixa3D(poseStack, bookConsumer, 0.0F, -0.185F, -0.022F, 0.255F, 0.185F, -0.008F, 0, 0, 16, 20, 64, 32, light);
        desenharCaixa3D(poseStack, bookConsumer, 0.004F, -0.168F, -0.008F, 0.238F, 0.168F, 0.018F, 12, 10, 24, 19, 64, 32, light);
        buffer.endBatch(RenderType.entitySolid(BOOK_TEXTURE));

        if (pagDirIndex < paginas.size()) {
            poseStack.pushPose();
            poseStack.translate(0.121F, 0.0F, 0.0195F);
            poseStack.scale(0.00215F, -0.00215F, 0.00215F);
            desenharLinhasComDestaque(font, paginas.get(pagDirIndex), poseStack.last().pose(), buffer, light, pagDirIndex + 1);
            poseStack.popPose();
        }
        poseStack.popPose();

        // --- FOLHA ANIMADA VIRANDO ---
        if (direcaoVirada != 0) {
            float prog = Mth.lerp(partialTick, animProgressoAnt, animProgresso);
            float anguloFolha = (direcaoVirada == 1)
                    ? Mth.lerp(prog, -13.0F, -167.0F)
                    : Mth.lerp(prog, -167.0F, -13.0F);

            poseStack.pushPose();
            poseStack.translate(0.0F, 0.0F, 0.020F);
            poseStack.mulPose(Axis.YP.rotationDegrees(anguloFolha));
            bookConsumer = buffer.getBuffer(RenderType.entitySolid(BOOK_TEXTURE));
            desenharCaixa3D(poseStack, bookConsumer, 0.002F, -0.166F, -0.002F, 0.234F, 0.166F, 0.002F, 12, 10, 24, 19, 64, 32, light);
            buffer.endBatch(RenderType.entitySolid(BOOK_TEXTURE));
            poseStack.popPose();
        }

        buffer.endBatch();
        poseStack.popPose();
    }

    private void renderizarBracoSegurandoLivro(PoseStack poseStack, MultiBufferSource.BufferSource buffer, int light, AbstractClientPlayer player, float side) {
        ResourceLocation skin = player.getSkinTextureLocation();
        VertexConsumer skinConsumer = buffer.getBuffer(RenderType.entityTranslucent(skin));

        poseStack.pushPose();
        poseStack.translate(side * -0.03F, -0.24F, -0.045F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * -14.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(16.0F));

        desenharCaixa3D(poseStack, skinConsumer, -0.052F, -0.28F, -0.052F, 0.052F, 0.05F, 0.052F, 44, 20, 48, 32, 64, 64, light);
        desenharCaixa3D(poseStack, skinConsumer, -0.056F, -0.28F, -0.056F, 0.056F, 0.055F, 0.056F, 44, 36, 48, 48, 64, 64, light);

        poseStack.popPose();
        buffer.endBatch(RenderType.entityTranslucent(skin));
    }

    // Desenha as letras com efeito de Tinta Forte (Semi-Negrito + Contorno de Contraste) para leitura fácil!
    private void desenharLinhasComDestaque(Font font, List<FormattedCharSequence> linhas, Matrix4f matrix, MultiBufferSource buffer, int light, int numPagina) {
        int espacoLinha = 10;
        float inicioY = -53.0F;
        float margemX = -LARGURA_TEXTO / 2.0F;

        float y = inicioY;
        for (FormattedCharSequence linha : linhas) {
            // 1ª Camada: Leve reforço lateral (deixa a letra mais encorpada/negrito suave para não ficar fina em 3D)
            font.drawInBatch(linha, margemX + 0.35F, y, 0x1A110B, false, matrix, buffer, Font.DisplayMode.NORMAL, 0, light);
            // 2ª Camada: Preto puro nítido por cima para contraste máximo sobre o papel creme
            font.drawInBatch(linha, margemX, y, 0x000000, false, matrix, buffer, Font.DisplayMode.NORMAL, 0, light);
            y += espacoLinha;
        }

        String rodape = "- " + numPagina + " -";
        float rodapeX = -font.width(rodape) / 2.0F;
        font.drawInBatch(rodape, rodapeX, 55.0F, 0x2B1E14, false, matrix, buffer, Font.DisplayMode.NORMAL, 0, light);
    }

    private void desenharCaixa3D(PoseStack poseStack, VertexConsumer consumer,
                                 float x1, float y1, float z1, float x2, float y2, float z2,
                                 int u1, int v1, int u2, int v2, float texW, float texH, int light) {
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();

        float minU = u1 / texW;
        float maxU = u2 / texW;
        float minV = v1 / texH;
        float maxV = v2 / texH;

        // Frente (Z = z2)
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
