package com.noletodocs.bookoverlay;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.BookModel;
import net.minecraft.client.model.geom.ModelLayers;
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
    private BookModel bookModel;

    private final List<List<FormattedCharSequence>> paginas = new ArrayList<>();
    private int spreadAtual = 0;
    private boolean livroAtivo = true;
    private boolean historiaCarregada = false;

    // Variáveis da animação 3D de virar a página
    private float flip = 0.0F;
    private float oFlip = 0.0F;
    private float flipT = 0.0F;
    private float flipA = 0.0F;

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

            // Largura de 80 pixels para ficar com margens bonitas dentro da folha
            List<FormattedCharSequence> todasLinhas = new ArrayList<>();
            String[] paragrafos = textoCompleto.replace("\r", "").split("\n");
            for (String p : paragrafos) {
                if (p.trim().isEmpty()) {
                    todasLinhas.add(FormattedCharSequence.EMPTY);
                } else {
                    todasLinhas.addAll(mc.font.split(Component.literal(p), 80));
                }
            }

            // 11 linhas por página
            int linhasPorPagina = 11;
            for (int i = 0; i < todasLinhas.size(); i += linhasPorPagina) {
                paginas.add(new ArrayList<>(todasLinhas.subList(i, Math.min(i + linhasPorPagina, todasLinhas.size()))));
            }

            if (paginas.isEmpty()) {
                paginas.add(mc.font.split(Component.literal("Sem texto."), 80));
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

        this.oFlip = this.flip;
        float f = (this.flipT - this.flip) * 0.4F;
        f = Mth.clamp(f, -0.2F, 0.2F);
        this.flipA += (f - this.flipA) * 0.9F;
        this.flip += this.flipA;
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
                    this.flipT += 1.0F;
                    mc.player.playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 1.0F);
                }
            } else if (event.getKey() == GLFW.GLFW_KEY_LEFT) {
                if (spreadAtual > 0) {
                    spreadAtual--;
                    this.flipT -= 1.0F;
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

        if (bookModel == null) {
            bookModel = new BookModel(mc.getEntityModels().bakeLayer(ModelLayers.BOOK));
        }

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        int light = LightTexture.FULL_BRIGHT;
        float partialTick = event.getPartialTick();

        poseStack.pushPose();

        boolean isRightHand = (event.getHand() == InteractionHand.MAIN_HAND)
                ? (mc.options.mainHand().get() == HumanoidArm.RIGHT)
                : (mc.options.mainHand().get() == HumanoidArm.LEFT);
        float side = isRightHand ? 1.0F : -1.0F;

        // Balanço suave da mão enquanto caminha
        float bob = Mth.sin((mc.player.tickCount + partialTick) * 0.1F) * 0.008F;

        // Posiciona o conjunto (Braço + Livro) na tela
        poseStack.translate(side * 0.35F, -0.22F + bob - (event.getEquipProgress() * 0.2F), -0.62F);

        // ÂNGULO CORRIGIDO: Livro em pé, virado de frente para o rosto do jogador (igual à 1ª imagem!)
        poseStack.mulPose(Axis.YP.rotationDegrees(side * -18.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(-8.0F)); // Negativo levanta o livro de frente para o olhar!
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * 12.0F));

        // 1) Renderiza o braço 3D do jogador segurando o livro por baixo
        renderizarBracoSegurando(poseStack, buffer, event.getPackedLight(), isRightHand, side);

        // 2) Renderiza o Modelo 3D do Livro
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
        poseStack.scale(0.82F, 0.82F, 0.82F);

        float pageFlip = Mth.lerp(partialTick, this.oFlip, this.flip);
        float leftPageFlip = Mth.frac(pageFlip + 0.25F) * 1.6F - 0.3F;
        float rightPageFlip = Mth.frac(pageFlip + 0.75F) * 1.6F - 0.3F;
        bookModel.setupAnim(0.0F, Mth.clamp(leftPageFlip, 0.0F, 1.0F), Mth.clamp(rightPageFlip, 0.0F, 1.0F), 1.0F);

        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entitySolid(BOOK_TEXTURE));
        bookModel.render(poseStack, vertexConsumer, light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        buffer.endBatch(RenderType.entitySolid(BOOK_TEXTURE));
        poseStack.popPose();

        // 3) Renderiza o texto DENTRO do mesmo sistema de rotação do livro (cola 100% na inclinação da folha!)
        renderizarTextoColadoNoPapel(poseStack, buffer, light);

        poseStack.popPose();
    }

    private void renderizarBracoSegurando(PoseStack poseStack, MultiBufferSource buffer, int packedLight, boolean isRightHand, float side) {
        try {
            Minecraft mc = Minecraft.getInstance();
            AbstractClientPlayer player = mc.player;
            if (player == null) return;

            EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
            PlayerRenderer playerRenderer = (PlayerRenderer) dispatcher.getRenderer(player);

            poseStack.pushPose();
            // Posiciona o braço saindo de baixo e segurando o meio do livro (igual à imagem de referência)
            poseStack.translate(side * 0.04F, -0.52F, 0.12F);
            poseStack.mulPose(Axis.XP.rotationDegrees(-65.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(side * 18.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(side * -12.0F));
            poseStack.scale(0.85F, 0.85F, 0.85F);

            if (isRightHand) {
                playerRenderer.renderRightHand(poseStack, buffer, packedLight, player);
            } else {
                playerRenderer.renderLeftHand(poseStack, buffer, packedLight, player);
            }
            poseStack.popPose();
        } catch (Exception ignored) {
        }
    }

    private void renderizarTextoColadoNoPapel(PoseStack poseStack, MultiBufferSource.BufferSource buffer, int light) {
        if (paginas.isEmpty()) return;
        Font font = Minecraft.getInstance().font;

        int pagEsqIndex = spreadAtual * 2;
        int pagDirIndex = spreadAtual * 2 + 1;

        // No BookModel com abertura 1.0F:
        // A lombada interna das páginas fica em Z = 0.05125F (0.0625 * 0.82).
        // Cada folha abre exatamente a 16.26 graus para trás a partir da lombada central.
        // Fazendo a rotação a partir da lombada central, o texto deita exatamente no plano da folha!

        // --- PÁGINA ESQUERDA ---
        if (pagEsqIndex < paginas.size()) {
            poseStack.pushPose();
            poseStack.translate(0.0F, 0.0F, 0.052F); // Vai até a lombada das páginas
            poseStack.mulPose(Axis.YP.rotationDegrees(-16.26F)); // Deita no ângulo exato da página esquerda!
            poseStack.translate(-0.205F, 0.165F, 0.002F); // Posiciona no topo esquerdo da folha
            poseStack.scale(0.00195F, -0.00195F, 0.00195F);

            desenharLinhasDaPagina(font, paginas.get(pagEsqIndex), poseStack.last().pose(), buffer, light, pagEsqIndex + 1);
            poseStack.popPose();
        }

        // --- PÁGINA DIREITA ---
        if (pagDirIndex < paginas.size()) {
            poseStack.pushPose();
            poseStack.translate(0.0F, 0.0F, 0.052F); // Vai até a lombada das páginas
            poseStack.mulPose(Axis.YP.rotationDegrees(16.26F)); // Deita no ângulo exato da página direita!
            poseStack.translate(0.022F, 0.165F, 0.002F); // Posiciona no topo direito da folha
            poseStack.scale(0.00195F, -0.00195F, 0.00195F);

            desenharLinhasDaPagina(font, paginas.get(pagDirIndex), poseStack.last().pose(), buffer, light, pagDirIndex + 1);
            poseStack.popPose();
        }

        RenderSystem.disableDepthTest();
        buffer.endBatch();
        RenderSystem.enableDepthTest();
    }

    private void desenharLinhasDaPagina(Font font, List<FormattedCharSequence> linhas, Matrix4f matrix, MultiBufferSource buffer, int light, int numPagina) {
        int y = 0;
        for (FormattedCharSequence linha : linhas) {
            font.drawInBatch(linha, 0, y, 0x2B2118, false, matrix, buffer, Font.DisplayMode.SEE_THROUGH, 0, light);
            y += 9;
        }
        String rodape = "- " + numPagina + " -";
        int larguraRodape = font.width(rodape);
        font.drawInBatch(rodape, (80 - larguraRodape) / 2.0F, 104, 0x6E5A47, false, matrix, buffer, Font.DisplayMode.SEE_THROUGH, 0, light);
    }
}
