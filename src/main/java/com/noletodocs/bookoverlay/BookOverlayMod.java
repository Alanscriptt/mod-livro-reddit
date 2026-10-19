package com.noletodocs.bookoverlay;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.BookModel;
import net.minecraft.client.model.geom.ModelLayers;
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

    private final List<List<FormattedCharSequence>> paginas = new ArrayList<>();
    private int spreadAtual = 0; // Cada spread mostra 2 páginas (Esquerda e Direita)
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

            // Largura ajustada (88 pixels) para caber com margem perfeita dentro da folha 3D
            List<FormattedCharSequence> todasLinhas = new ArrayList<>();
            String[] paragrafos = textoCompleto.replace("\r", "").split("\n");
            for (String p : paragrafos) {
                if (p.trim().isEmpty()) {
                    todasLinhas.add(FormattedCharSequence.EMPTY);
                } else {
                    todasLinhas.addAll(mc.font.split(Component.literal(p), 88));
                }
            }

            // 10 linhas por página para ficar bem legível no vídeo
            int linhasPorPagina = 10;
            for (int i = 0; i < todasLinhas.size(); i += linhasPorPagina) {
                paginas.add(new ArrayList<>(todasLinhas.subList(i, Math.min(i + linhasPorPagina, todasLinhas.size()))));
            }

            if (paginas.isEmpty()) {
                paginas.add(mc.font.split(Component.literal("Sem texto."), 88));
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

        // Física da animação da página virando
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
        MultiBufferSource buffer = event.getMultiBufferSource();
        // Usa iluminação clara para que dê para ler perfeitamente mesmo de noite ou em cavernas!
        int light = LightTexture.FULL_BRIGHT;
        float partialTick = event.getPartialTick();

        poseStack.pushPose();

        boolean isRightHand = (event.getHand() == InteractionHand.MAIN_HAND)
                ? (mc.options.mainHand().get() == HumanoidArm.RIGHT)
                : (mc.options.mainHand().get() == HumanoidArm.LEFT);
        float side = isRightHand ? 1.0F : -1.0F;

        // Mais alto na tela (-0.24F) para não cortar a parte de baixo do livro
        float bob = Mth.sin((mc.player.tickCount + partialTick) * 0.1F) * 0.01F;
        poseStack.translate(side * 0.36F, -0.24F + bob - (event.getEquipProgress() * 0.2F), -0.62F);

        // Inclinação confortável de leitura em 1ª pessoa
        poseStack.mulPose(Axis.YP.rotationDegrees(side * -14.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(18.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * 5.0F));

        // Renderiza o Modelo 3D do Livro
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
        poseStack.scale(0.85F, 0.85F, 0.85F);

        float pageFlip = Mth.lerp(partialTick, this.oFlip, this.flip);
        float leftPageFlip = Mth.frac(pageFlip + 0.25F) * 1.6F - 0.3F;
        float rightPageFlip = Mth.frac(pageFlip + 0.75F) * 1.6F - 0.3F;
        bookModel.setupAnim(0.0F, Mth.clamp(leftPageFlip, 0.0F, 1.0F), Mth.clamp(rightPageFlip, 0.0F, 1.0F), 1.0F);

        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entitySolid(BOOK_TEXTURE));
        bookModel.render(poseStack, vertexConsumer, light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();

        // Renderiza o texto perfeitamente alinhado sobre a superfície 3D das folhas
        renderizarTextoNasPaginas(poseStack, buffer, light);

        poseStack.popPose();
    }

    private void renderizarTextoNasPaginas(PoseStack poseStack, MultiBufferSource buffer, int light) {
        if (paginas.isEmpty()) return;
        Font font = Minecraft.getInstance().font;

        int pagEsqIndex = spreadAtual * 2;
        int pagDirIndex = spreadAtual * 2 + 1;

        // O modelo do livro aberto tem uma inclinação de -10.5 graus para trás em cada folha a partir do centro.
        // Fazemos a rotação a partir do centro (X=0) e colocamos Z=0.092F para ficar na frente do papel sem cortar!

        // --- PÁGINA ESQUERDA ---
        if (pagEsqIndex < paginas.size()) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(-10.5F)); // Segue o ângulo exato da página esquerda do BookModel
            poseStack.translate(-0.235F, 0.165F, 0.092F);
            poseStack.scale(0.0021F, -0.0021F, 0.0021F);

            desenharLinhasDaPagina(font, paginas.get(pagEsqIndex), poseStack.last().pose(), buffer, light, pagEsqIndex + 1);
            poseStack.popPose();
        }

        // --- PÁGINA DIREITA ---
        if (pagDirIndex < paginas.size()) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(10.5F)); // Segue o ângulo exato da página direita do BookModel
            poseStack.translate(0.032F, 0.165F, 0.092F);
            poseStack.scale(0.0021F, -0.0021F, 0.0021F);

            desenharLinhasDaPagina(font, paginas.get(pagDirIndex), poseStack.last().pose(), buffer, light, pagDirIndex + 1);
            poseStack.popPose();
        }
    }

    private void desenharLinhasDaPagina(Font font, List<FormattedCharSequence> linhas, Matrix4f matrix, MultiBufferSource buffer, int light, int numPagina) {
        int y = 0;
        for (FormattedCharSequence linha : linhas) {
            font.drawInBatch(linha, 0, y, 0x1E1610, false, matrix, buffer, Font.DisplayMode.NORMAL, 0, light);
            y += 10;
        }
        String rodape = "- " + numPagina + " -";
        int larguraRodape = font.width(rodape);
        font.drawInBatch(rodape, (88 - larguraRodape) / 2.0F, 110, 0x5C4938, false, matrix, buffer, Font.DisplayMode.NORMAL, 0, light);
    }
}
