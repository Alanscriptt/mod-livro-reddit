package com.noletodocs.bookoverlay;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.BookModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
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
    private ModelPart bookRoot;
    private ModelPart leftPagePart;
    private ModelPart rightPagePart;
    private ModelPart flipPage1Part;
    private ModelPart flipPage2Part;

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

            List<FormattedCharSequence> todasLinhas = new ArrayList<>();
            String[] paragrafos = textoCompleto.replace("\r", "").split("\n");
            for (String p : paragrafos) {
                if (p.trim().isEmpty()) {
                    todasLinhas.add(FormattedCharSequence.EMPTY);
                } else {
                    todasLinhas.addAll(mc.font.split(Component.literal(p), 82));
                }
            }

            int linhasPorPagina = 10;
            for (int i = 0; i < todasLinhas.size(); i += linhasPorPagina) {
                paginas.add(new ArrayList<>(todasLinhas.subList(i, Math.min(i + linhasPorPagina, todasLinhas.size()))));
            }

            if (paginas.isEmpty()) {
                paginas.add(mc.font.split(Component.literal("Sem texto."), 82));
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
            bookRoot = mc.getEntityModels().bakeLayer(ModelLayers.BOOK);
            bookModel = new BookModel(bookRoot);
            leftPagePart = bookRoot.getChild("left_page");
            rightPagePart = bookRoot.getChild("right_page");
            flipPage1Part = bookRoot.getChild("flip_page1");
            flipPage2Part = bookRoot.getChild("flip_page2");
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

        // Posiciona o livro inteiro visível na tela (sem cortar embaixo nem nos lados)
        float bob = Mth.sin((mc.player.tickCount + partialTick) * 0.1F) * 0.008F;
        poseStack.translate(side * 0.32F, -0.15F + bob - (event.getEquipProgress() * 0.2F), -0.65F);

        // Rotaciona o livro na direção dos olhos do jogador
        poseStack.mulPose(Axis.YP.rotationDegrees(side * -12.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(22.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * 4.0F));

        // Orientação base do BookModel
        poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
        poseStack.scale(0.75F, 0.75F, 0.75F);

        float pageFlip = Mth.lerp(partialTick, this.oFlip, this.flip);
        float leftPageFlip = Mth.frac(pageFlip + 0.25F) * 1.6F - 0.3F;
        float rightPageFlip = Mth.frac(pageFlip + 0.75F) * 1.6F - 0.3F;
        bookModel.setupAnim(0.0F, Mth.clamp(leftPageFlip, 0.0F, 1.0F), Mth.clamp(rightPageFlip, 0.0F, 1.0F), 1.0F);

        // Só mostra as folhas soltas do meio ENQUANTO a página está virando,
        // para não ficarem levantadas tampando o texto quando o livro está parado!
        boolean virandoPagina = Math.abs(this.flipT - this.flip) > 0.02F;
        flipPage1Part.visible = virandoPagina;
        flipPage2Part.visible = virandoPagina;

        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entitySolid(BOOK_TEXTURE));
        bookModel.render(poseStack, vertexConsumer, light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        // Força o desenho do modelo 3D do livro ANTES do texto!
        buffer.endBatch(RenderType.entitySolid(BOOK_TEXTURE));

        // Desenha o texto ancorado diretamente no ModelPart de cada página 3D!
        renderizarTextoAncoradoNasPaginas(poseStack, buffer, light);

        poseStack.popPose();
    }

    private void renderizarTextoAncoradoNasPaginas(PoseStack poseStack, MultiBufferSource.BufferSource buffer, int light) {
        if (paginas.isEmpty()) return;
        Font font = Minecraft.getInstance().font;

        int pagEsqIndex = spreadAtual * 2;
        int pagDirIndex = spreadAtual * 2 + 1;

        // --- PÁGINA ESQUERDA (usa a matriz exata da peça left_page) ---
        if (pagEsqIndex < paginas.size() && leftPagePart != null) {
            poseStack.pushPose();
            leftPagePart.translateAndRotate(poseStack); // Cola perfeitamente na angulação da folha esquerda!
            poseStack.translate(0.064F, -0.23F, -0.04F);
            poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(-90.0F));
            poseStack.scale(0.0022F, 0.0022F, 0.0022F);

            desenharLinhasDaPagina(font, paginas.get(pagEsqIndex), poseStack.last().pose(), buffer, light, pagEsqIndex + 1);
            poseStack.popPose();
        }

        // --- PÁGINA DIREITA (usa a matriz exata da peça right_page) ---
        if (pagDirIndex < paginas.size() && rightPagePart != null) {
            poseStack.pushPose();
            rightPagePart.translateAndRotate(poseStack); // Cola perfeitamente na angulação da folha direita!
            poseStack.translate(-0.064F, -0.23F, -0.265F);
            poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
            poseStack.scale(0.0022F, 0.0022F, 0.0022F);

            desenharLinhasDaPagina(font, paginas.get(pagDirIndex), poseStack.last().pose(), buffer, light, pagDirIndex + 1);
            poseStack.popPose();
        }

        buffer.endBatch();
    }

    private void desenharLinhasDaPagina(Font font, List<FormattedCharSequence> linhas, Matrix4f matrix, MultiBufferSource buffer, int light, int numPagina) {
        int y = 0;
        for (FormattedCharSequence linha : linhas) {
            // DisplayMode.POLYGON_OFFSET garante que a letra nunca afunde na textura do papel!
            font.drawInBatch(linha, 0, y, 0x1E1610, false, matrix, buffer, Font.DisplayMode.POLYGON_OFFSET, 0, light);
            y += 10;
        }
        String rodape = "- " + numPagina + " -";
        int larguraRodape = font.width(rodape);
        font.drawInBatch(rodape, (82 - larguraRodape) / 2.0F, 106, 0x5C4938, false, matrix, buffer, Font.DisplayMode.POLYGON_OFFSET, 0, light);
    }
}
