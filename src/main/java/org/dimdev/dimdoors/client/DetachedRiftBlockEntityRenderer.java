package org.dimdev.dimdoors.client;

import com.flowpowered.math.TrigMath;
import net.minecraft.util.math.Matrix4f;
import org.dimdev.dimdoors.ModConfig;
import org.dimdev.dimdoors.block.entity.DetachedRiftBlockEntity;
import org.dimdev.dimdoors.client.tesseract.Tesseract;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.dimdev.dimdoors.util.RGBA;

@Environment(EnvType.CLIENT)
public class DetachedRiftBlockEntityRenderer extends BlockEntityRenderer<DetachedRiftBlockEntity> {
    public static final Identifier TESSERACT_PATH = new Identifier("dimdoors:textures/other/tesseract.png");
    private RGBA COLOR = new RGBA(1, 0.5f, 1, 1);

    private static final Tesseract TESSERACT = new Tesseract();
    private static final RiftCurves.PolygonInfo CURVE = RiftCurves.CURVES.get(0);
    public static long showRiftCoreUntil = 0;

    public DetachedRiftBlockEntityRenderer(BlockEntityRenderDispatcher dispatcher) {
        super(dispatcher);
    }

    @Override
    public void render(DetachedRiftBlockEntity rift, float tickDelta, MatrixStack matrices, VertexConsumerProvider vcs, int breakProgress, int alpha) {
        Matrix4f model = matrices.peek().getModel();

        if (ModConfig.GRAPHICS.showRiftCore) {
            renderTesseract(vcs.getBuffer(MyRenderLayer.TESSERACT), rift, matrices, tickDelta);
        } else {
            long timeLeft = showRiftCoreUntil - System.currentTimeMillis();
            if (timeLeft >= 0) {
                renderTesseract(vcs.getBuffer(MyRenderLayer.TESSERACT), rift, matrices, tickDelta);
            }
        }

        renderCrack(vcs.getBuffer(MyRenderLayer.CRACK), matrices, rift);
    }

    private void renderCrack(VertexConsumer vc, MatrixStack matrices, DetachedRiftBlockEntity rift) {
        matrices.push();
        matrices.translate(0.5, 0.5, 0.5);
        RiftCrackRenderer.drawCrack(matrices.peek().getModel(), vc, 0, CURVE, ModConfig.GRAPHICS.riftSize * rift.size, 0xF1234568L * rift.getPos().hashCode());
        matrices.pop();
    }

    private void renderTesseract( VertexConsumer vc, DetachedRiftBlockEntity rift, MatrixStack matrices, float tickDelta) {
        double radian = nextAngle(rift, tickDelta) * TrigMath.DEG_TO_RAD;
        RGBA color = rift.getColor();
        if (color == RGBA.NONE) color = COLOR;

        matrices.push();

        matrices.translate(0.5, 0.5, 0.5);
        matrices.scale(0.25f, 0.25f, 0.25f);

        TESSERACT.draw(matrices.peek().getModel(), vc, color, radian);

        matrices.pop();
    }

    private double nextAngle(DetachedRiftBlockEntity rift, float partialTicks) {
        rift.renderAngle = (rift.renderAngle + 5 * partialTicks) % 360;
        return rift.renderAngle;
    }
}
