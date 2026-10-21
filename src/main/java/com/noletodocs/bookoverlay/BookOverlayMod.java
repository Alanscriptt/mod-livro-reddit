package com.noletodocs.bookoverlay;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
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
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@Mod("examplemod")
public class BookOverlayMod {

    private static final ResourceLocation BOOK_TEXTURE = new ResourceLocation("textures/entity/enchanting_table_book.png");
    private ResourceLocation solidPageTexture;

    private final List<List<FormattedCharSequence>> paginas = new ArrayList<>();
    private int spreadAtual = 0;
    private boolean livroAtivo = true;
    private boolean historiaCarregada = false;

    private float animProgresso = 0.0F;
    private float animProgressoAnt = 0.0F;
    private int direcaoVirada = 0;

    private static final int LARGURA_TEXTO = 88;
    private static final int MAX_LINHAS_POR_PAGINA = 10;

    public BookOverlayMod() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            MinecraftForge.EVENT_BUS.register(this);
        }
    }

    private ResourceLocation getSolidPageTexture() {
        if (solidPageTexture == null) {
            NativeImage img = new NativeImage(1, 1, false);
            img.setPixelRGBA(0, 0, 0xFFFFFFFF);
            DynamicTexture dyn = new DynamicTexture(img);
            solidPageTexture = Minecraft.getInstance().getTextureManager().register("solid_book_page", dyn);
        }
        return solidPageTexture;
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
                textoCompleto = "Crie o ficheiro historia.txt na pasta do modpack!\n\nAgora nenhuma frase é cortada a meio entre uma página e outra.";
            }

            if (textoCompleto.trim().isEmpty()) {
                textoCompleto = "O ficheiro historia.txt está vazio.";
            }

            List<FormattedCharSequence> linhasFixasNaPagina = new ArrayList<>();
            String[] paragrafos = textoCompleto.replace("\r", "").split("\n");

            for (String paragrafo : paragrafos) {
                String pLimpo = paragrafo.trim();

                if (pLimpo.isEmpty()) {
                    // Linha em branco entre parágrafos (só adiciona se a página já tiver texto e espaço)
                    if (!linhasFixasNaPagina.isEmpty() && linhasFixasNaPagina.size() < MAX_LINHAS_POR_PAGINA - 1) {
                        linhasFixasNaPagina.add(FormattedCharSequence.EMPTY);
                    }
                    continue;
                }

                // Divide o parágrafo em frases completas (terminadas em ., !, ?, : ou …)
                String[] frases = pLimpo.split("(?<=[.!?…:])\\s+");
                StringBuilder paragrafoNaPaginaAtual = new StringBuilder();

                for (String frase : frases) {
                    String testeParagrafo = paragrafoNaPaginaAtual.length() == 0
                            ? frase
                            : paragrafoNaPaginaAtual + " " + frase;

                    List<FormattedCharSequence> linhasTeste = mc.font.split(Component.literal(testeParagrafo), LARGURA_TEXTO);

                    // Verifica se a frase inteira cabe no que resta da página atual
                    if (linhasFixasNaPagina.size() + linhasTeste.size() <= MAX_LINHAS_POR_PAGINA) {
                        paragrafoNaPaginaAtual.setLength(0);
                        paragrafoNaPaginaAtual.append(testeParagrafo);
                    } else {
                        // A nova frase não cabe inteira! Fecha a página atual no ponto final anterior:
                        if (paragrafoNaPaginaAtual.length() > 0) {
                            linhasFixasNaPagina.addAll(mc.font.split(Component.literal(paragrafoNaPaginaAtual.toString()), LARGURA_TEXTO));
                            paragrafoNaPaginaAtual.setLength(0);
                        }
                        if (!linhasFixasNaPagina.isEmpty()) {
                            removerLinhasVaziasDoFim(linhasFixasNaPagina);
                            if (!linhasFixasNaPagina.isEmpty()) {
                                paginas.add(new ArrayList<>(linhasFixasNaPagina));
                            }
                            linhasFixasNaPagina.clear();
                        }

                        // Verifica se a frase sozinha é maior do que uma página inteira (> 10 linhas)
                        List<FormattedCharSequence> linhasFraseSozinha = mc.font.split(Component.literal(frase), LARGURA_TEXTO);
                        if (linhasFraseSozinha.size() <= MAX_LINHAS_POR_PAGINA) {
                            paragrafoNaPaginaAtual.append(frase);
                        } else {
                            // Caso raro de frase gigante sem ponto final: divide por vírgulas para não cortar palavras a meio
                            String[] pausas = frase.split("(?<=[,;—-])\\s+");
                            for (String pausa : pausas) {
                                String testePausa = paragrafoNaPaginaAtual.length() == 0
                                        ? pausa
                                        : paragrafoNaPaginaAtual + " " + pausa;
                                List<FormattedCharSequence> linhasPausa = mc.font.split(Component.literal(testePausa), LARGURA_TEXTO);
                                if (linhasPausa.size() <= MAX_LINHAS_POR_PAGINA) {
                                    paragrafoNaPaginaAtual.setLength(0);
                                    paragrafoNaPaginaAtual.append(testePausa);
                                } else {
                                    if (paragrafoNaPaginaAtual.length() > 0) {
                                        paginas.add(new ArrayList<>(mc.font.split(Component.literal(paragrafoNaPaginaAtual.toString()), LARGURA_TEXTO)));
                                        paragrafoNaPaginaAtual.setLength(0);
                                    }
                                    paragrafoNaPaginaAtual.append(pausa);
                                }
                            }
                        }
                    }
                }

                // Ao terminar o parágrafo, fixa as linhas dele na página atual
                if (paragrafoNaPaginaAtual.length() > 0) {
                    linhasFixasNaPagina.addAll(mc.font.split(Component.literal(paragrafoNaPaginaAtual.toString()), LARGURA_TEXTO));
                }

                if (linhasFixasNaPagina.size() >= MAX_LINHAS_POR_PAGINA) {
                    removerLinhasVaziasDoFim(linhasFixasNaPagina);
                    paginas.add(new ArrayList<>(linhasFixasNaPagina));
                    linhasFixasNaPagina.clear();
                }
            }

            if (!linhasFixasNaPagina.isEmpty()) {
                removerLinhasVaziasDoFim(linhasFixasNaPagina);
                if (!linhasFixasNaPagina.isEmpty()) {
                    paginas.add(new ArrayList<>(linhasFixasNaPagina));
                }
            }

            if (paginas.isEmpty()) {
                paginas.add(mc.font.split(Component.literal("Sem texto."), LARGURA_TEXTO));
            }
            historiaCarregada = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void removerLinhasVaziasDoFim(List<FormattedCharSequence> linhas) {
        while (!linhas.isEmpty() && linhas.get(linhas.size() - 1) == FormattedCharSequence.EMPTY) {
            linhas.remove(linhas.size() - 1);
        }
    }

    private void avancarPagina(Minecraft mc) {
        if ((spreadAtual + 1) * 2 < paginas.size()) {
            spreadAtual++;
            direcaoVirada = 1;
            animProgresso = 0.01F;
            animProgressoAnt = 0.01F;
            mc.player.playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 1.0F);
        }
    }

    private void voltarPagina(Minecraft mc) {
        if (spreadAtual > 0) {
            spreadAtual--;
            direcaoVirada = -1;
            animProgresso = 0.01F;
            animProgressoAnt = 0.01F;
            mc.player.playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.9F);
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
                avancarPagina(mc);
            } else if (event.getKey() == GLFW.GLFW_KEY_LEFT) {
                voltarPagina(mc);
            } else if (event.getKey() == GLFW.GLFW_KEY_R) {
                carregarHistoria();
                spreadAtual = 0;
                mc.player.displayClientMessage(Component.literal("§a[Relatos] historia.txt recarregado!"), true);
            }
        }
    }

    @SubscribeEvent
    public void onMouseInput(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (!livroAtivo || mc.player == null || mc.screen != null) return;

        if (event.getAction() == GLFW.GLFW_PRESS) {
            if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_5) {
                avancarPagina(mc);
                event.setCanceled(true);
            } else if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_4) {
                voltarPagina(mc);
                event.setCanceled(true);
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

        poseStack.last().pose().setIdentity();
        poseStack.last().normal().setIdentity();

        boolean isRightHand = (event.getHand() == InteractionHand.MAIN_HAND)
                ? (mc.options.mainHand().get() == HumanoidArm.RIGHT)
                : (mc.options.mainHand().get() == HumanoidArm.LEFT);
        float side = isRightHand ? 1.0F : -1.0F;

        poseStack.translate(side * 0.31F, -0.14F, -0.58F);

        poseStack.mulPose(Vector3f.YP.rotationDegrees(side * -18.0F));
        poseStack.mulPose(Vector3f.XP.rotationDegrees(-14.0F));
        poseStack.mulPose(Vector3f.ZP.rotationDegrees(side * 8.0F));

        renderizarBracoSegurandoLivro(poseStack, buffer, light, mc.player, side);

        Font font = mc.font;
        int pagEsqIndex = spreadAtual * 2;
        int pagDirIndex = spreadAtual * 2 + 1;
        ResourceLocation pageTex = getSolidPageTexture();

        // --- LADO ESQUERDO ---
        poseStack.pushPose();
        poseStack.mulPose(Vector3f.YP.rotationDegrees(13.0F));

        VertexConsumer bookConsumer = buffer.getBuffer(RenderType.entitySolid(BOOK_TEXTURE));
        desenharCaixa3D(poseStack, bookConsumer, -0.255F, -0.185F, -0.022F, 0.0F, 0.185F, -0.008F, 0, 0, 16, 20, 64, 32, 255, 255, 255, light);
        buffer.endBatch(RenderType.entitySolid(BOOK_TEXTURE));

        VertexConsumer pageConsumer = buffer.getBuffer(RenderType.entitySolid(pageTex));
        desenharPaginaLisa3D(poseStack, pageConsumer, -0.238F, -0.168F, -0.008F, -0.004F, 0.168F, 0.018F, 213, 203, 166, light);
        buffer.endBatch(RenderType.entitySolid(pageTex));

        if (pagEsqIndex < paginas.size()) {
            poseStack.pushPose();
            poseStack.translate(-0.121F, 0.0F, 0.0195F);
            poseStack.scale(0.00215F, -0.00215F, 0.00215F);
            desenharLinhasComDestaque(font, paginas.get(pagEsqIndex), poseStack.last().pose(), buffer, light, pagEsqIndex + 1);
            poseStack.popPose();
        }
        poseStack.popPose();

        // --- LADO DIREITO ---
        poseStack.pushPose();
        poseStack.mulPose(Vector3f.YP.rotationDegrees(-13.0F));

        bookConsumer = buffer.getBuffer(RenderType.entitySolid(BOOK_TEXTURE));
        desenharCaixa3D(poseStack, bookConsumer, 0.0F, -0.185F, -0.022F, 0.255F, 0.185F, -0.008F, 0, 0, 16, 20, 64, 32, 255, 255, 255, light);
        buffer.endBatch(RenderType.entitySolid(BOOK_TEXTURE));

        pageConsumer = buffer.getBuffer(RenderType.entitySolid(pageTex));
        desenharPaginaLisa3D(poseStack, pageConsumer, 0.004F, -0.168F, -0.008F, 0.238F, 0.168F, 0.018F, 213, 203, 166, light);
        buffer.endBatch(RenderType.entitySolid(pageTex));

        if (pagDirIndex < paginas.size()) {
            poseStack.pushPose();
            poseStack.translate(0.121F, 0.0F, 0.0195F);
            poseStack.scale(0.00215F, -0.00215F, 0.00215F);
            desenharLinhasComDestaque(font, paginas.get(pagDirIndex), poseStack.last().pose(), buffer, light, pagDirIndex + 1);
            poseStack.popPose();
        }
        poseStack.popPose();

        // --- FOLHA VIRANDO ---
        if (direcaoVirada != 0) {
            float prog = Mth.lerp(partialTick, animProgressoAnt, animProgresso);
            float anguloFolha = (direcaoVirada == 1)
                    ? Mth.lerp(prog, -13.0F, -167.0F)
                    : Mth.lerp(prog, -167.0F, -13.0F);

            poseStack.pushPose();
            poseStack.translate(0.0F, 0.0F, 0.020F);
            poseStack.mulPose(Vector3f.YP.rotationDegrees(anguloFolha));
            pageConsumer = buffer.getBuffer(RenderType.entitySolid(pageTex));
            desenharPaginaLisa3D(poseStack, pageConsumer, 0.002F, -0.166F, -0.002F, 0.234F, 0.166F, 0.002F, 213, 203, 166, light);
            buffer.endBatch(RenderType.entitySolid(pageTex));
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
        poseStack.mulPose(Vector3f.ZP.rotationDegrees(side * -14.0F));
        poseStack.mulPose(Vector3f.XP.rotationDegrees(16.0F));

        desenharCaixa3D(poseStack, skinConsumer, -0.052F, -0.28F, -0.052F, 0.052F, 0.05F, 0.052F, 44, 20, 48, 32, 64, 64, 255, 255, 255, light);
        desenharCaixa3D(poseStack, skinConsumer, -0.056F, -0.28F, -0.056F, 0.056F, 0.055F, 0.056F, 44, 36, 48, 48, 64, 64, 255, 255, 255, light);

        poseStack.popPose();
        buffer.endBatch(RenderType.entityTranslucent(skin));
    }

    private void desenharLinhasComDestaque(Font font, List<FormattedCharSequence> linhas, Matrix4f matrix, MultiBufferSource buffer, int light, int numPagina) {
        int espacoLinha = 10;
        float inicioY = -53.0F;
        float margemX = -LARGURA_TEXTO / 2.0F;

        float y = inicioY;
        for (FormattedCharSequence linha : linhas) {
            font.drawInBatch(linha, margemX + 0.35F, y, 0x1A110B, false, matrix, buffer, false, 0, light);
            font.drawInBatch(linha, margemX, y, 0x000000, false, matrix, buffer, false, 0, light);
            y += espacoLinha;
        }

        String rodape = "- " + numPagina + " -";
        float rodapeX = -font.width(rodape) / 2.0F;
        font.drawInBatch(rodape, rodapeX, 55.0F, 0x2B1E14, false, matrix, buffer, false, 0, light);
    }

    private void desenharPaginaLisa3D(PoseStack poseStack, VertexConsumer consumer,
                                      float x1, float y1, float z1, float x2, float y2, float z2,
                                      int r, int g, int b, int light) {
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();

        int rSide = (int) (r * 0.88F);
        int gSide = (int) (g * 0.88F);
        int bSide = (int) (b * 0.88F);

        verticeCor(consumer, pose, normal, x1, y1, z2, 0, 1, 0, 0, 1, r, g, b, light);
        verticeCor(consumer, pose, normal, x2, y1, z2, 1, 1, 0, 0, 1, r, g, b, light);
        verticeCor(consumer, pose, normal, x2, y2, z2, 1, 0, 0, 0, 1, r, g, b, light);
        verticeCor(consumer, pose, normal, x1, y2, z2, 0, 0, 0, 0, 1, r, g, b, light);

        verticeCor(consumer, pose, normal, x2, y1, z1, 0, 1, 0, 0, -1, r, g, b, light);
        verticeCor(consumer, pose, normal, x1, y1, z1, 1, 1, 0, 0, -1, r, g, b, light);
        verticeCor(consumer, pose, normal, x1, y2, z1, 1, 0, 0, 0, -1, r, g, b, light);
        verticeCor(consumer, pose, normal, x2, y2, z1, 0, 0, 0, 0, -1, r, g, b, light);

        verticeCor(consumer, pose, normal, x1, y2, z2, 0, 1, 0, 1, 0, rSide, gSide, bSide, light);
        verticeCor(consumer, pose, normal, x2, y2, z2, 1, 1, 0, 1, 0, rSide, gSide, bSide, light);
        verticeCor(consumer, pose, normal, x2, y2, z1, 1, 0, 0, 1, 0, rSide, gSide, bSide, light);
        verticeCor(consumer, pose, normal, x1, y2, z1, 0, 0, 0, 1, 0, rSide, gSide, bSide, light);

        verticeCor(consumer, pose, normal, x1, y1, z1, 0, 1, 0, -1, 0, rSide, gSide, bSide, light);
        verticeCor(consumer, pose, normal, x2, y1, z1, 1, 1, 0, -1, 0, rSide, gSide, bSide, light);
        verticeCor(consumer, pose, normal, x2, y1, z2, 1, 0, 0, -1, 0, rSide, gSide, bSide, light);
        verticeCor(consumer, pose, normal, x1, y1, z2, 0, 0, 0, -1, 0, rSide, gSide, bSide, light);

        verticeCor(consumer, pose, normal, x1, y1, z1, 0, 1, -1, 0, 0, rSide, gSide, bSide, light);
        verticeCor(consumer, pose, normal, x1, y1, z2, 1, 1, -1, 0, 0, rSide, gSide, bSide, light);
        verticeCor(consumer, pose, normal, x1, y2, z2, 1, 0, -1, 0, 0, rSide, gSide, bSide, light);
        verticeCor(consumer, pose, normal, x1, y2, z1, 0, 0, -1, 0, 0, rSide, gSide, bSide, light);

        verticeCor(consumer, pose, normal, x2, y1, z2, 0, 1, 1, 0, 0, rSide, gSide, bSide, light);
        verticeCor(consumer, pose, normal, x2, y1, z1, 1, 1, 1, 0, 0, rSide, gSide, bSide, light);
        verticeCor(consumer, pose, normal, x2, y2, z1, 1, 0, 1, 0, 0, rSide, gSide, bSide, light);
        verticeCor(consumer, pose, normal, x2, y2, z2, 0, 0, 1, 0, 0, rSide, gSide, bSide, light);
    }

    private void desenharCaixa3D(PoseStack poseStack, VertexConsumer consumer,
                                 float x1, float y1, float z1, float x2, float y2, float z2,
                                 int u1, int v1, int u2, int v2, float texW, float texH,
                                 int r, int g, int b, int light) {
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();

        float minU = u1 / texW;
        float maxU = u2 / texW;
        float minV = v1 / texH;
        float maxV = v2 / texH;

        verticeCor(consumer, pose, normal, x1, y1, z2, minU, maxV, 0, 0, 1, r, g, b, light);
        verticeCor(consumer, pose, normal, x2, y1, z2, maxU, maxV, 0, 0, 1, r, g, b, light);
        verticeCor(consumer, pose, normal, x2, y2, z2, maxU, minV, 0, 0, 1, r, g, b, light);
        verticeCor(consumer, pose, normal, x1, y2, z2, minU, minV, 0, 0, 1, r, g, b, light);

        verticeCor(consumer, pose, normal, x2, y1, z1, minU, maxV, 0, 0, -1, r, g, b, light);
        verticeCor(consumer, pose, normal, x1, y1, z1, maxU, maxV, 0, 0, -1, r, g, b, light);
        verticeCor(consumer, pose, normal, x1, y2, z1, maxU, minV, 0, 0, -1, r, g, b, light);
        verticeCor(consumer, pose, normal, x2, y2, z1, minU, minV, 0, 0, -1, r, g, b, light);

        verticeCor(consumer, pose, normal, x1, y2, z2, minU, maxV, 0, 1, 0, r, g, b, light);
        verticeCor(consumer, pose, normal, x2, y2, z2, maxU, maxV, 0, 1, 0, r, g, b, light);
        verticeCor(consumer, pose, normal, x2, y2, z1, maxU, minV, 0, 1, 0, r, g, b, light);
        verticeCor(consumer, pose, normal, x1, y2, z1, minU, minV, 0, 1, 0, r, g, b, light);

        verticeCor(consumer, pose, normal, x1, y1, z1, minU, maxV, 0, -1, 0, r, g, b, light);
        verticeCor(consumer, pose, normal, x2, y1, z1, maxU, maxV, 0, -1, 0, r, g, b, light);
        verticeCor(consumer, pose, normal, x2, y1, z2, maxU, minV, 0, -1, 0, r, g, b, light);
        verticeCor(consumer, pose, normal, x1, y1, z2, minU, minV, 0, -1, 0, r, g, b, light);

        verticeCor(consumer, pose, normal, x1, y1, z1, minU, maxV, -1, 0, 0, r, g, b, light);
        verticeCor(consumer, pose, normal, x1, y1, z2, maxU, maxV, -1, 0, 0, r, g, b, light);
        verticeCor(consumer, pose, normal, x1, y2, z2, maxU, minV, -1, 0, 0, r, g, b, light);
        verticeCor(consumer, pose, normal, x1, y2, z1, minU, minV, -1, 0, 0, r, g, b, light);

        verticeCor(consumer, pose, normal, x2, y1, z2, minU, maxV, 1, 0, 0, r, g, b, light);
        verticeCor(consumer, pose, normal, x2, y1, z1, maxU, maxV, 1, 0, 0, r, g, b, light);
        verticeCor(consumer, pose, normal, x2, y2, z1, maxU, minV, 1, 0, 0, r, g, b, light);
        verticeCor(consumer, pose, normal, x2, y2, z2, minU, minV, 1, 0, 0, r, g, b, light);
    }

    private void verticeCor(VertexConsumer consumer, Matrix4f pose, Matrix3f normal,
                            float x, float y, float z, float u, float v,
                            float nx, float ny, float nz, int r, int g, int b, int light) {
        consumer.vertex(pose, x, y, z)
                .color(r, g, b, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(normal, nx, ny, nz)
                .endVertex();
    }
}
