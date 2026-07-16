package dev.wxdao.ccsmartcard.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.wxdao.ccsmartcard.block.GateCabinetBlock;
import dev.wxdao.ccsmartcard.block.entity.GateCabinetBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/** Renders the retracting glass wing; managed barrier blocks remain collision-only. */
public final class GateCabinetRenderer implements BlockEntityRenderer<GateCabinetBlockEntity> {
    private static final ResourceLocation GLASS_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/block/light_blue_stained_glass.png");
    private static final ResourceLocation TRIM_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png");
    private static final float BARRIER_BOTTOM = 4.0F / 16.0F;
    private static final float BARRIER_TOP = 20.0F / 16.0F;
    private static final float HALF_THICKNESS = 0.75F / 16.0F;
    private static final float TRIM_SIZE = 1.0F / 16.0F;
    private static final float TRIM_HALF_THICKNESS = HALF_THICKNESS + 0.5F / 16.0F;
    private static final float SAFETY_BAND_BOTTOM = 10.5F / 16.0F;
    private static final float SAFETY_BAND_TOP = 12.0F / 16.0F;
    private static final int GLASS_COLOUR = 0xB8FFFFFF;
    private static final int TRIM_COLOUR = 0xD84DDDE8;
    private static final int SAFETY_BAND_COLOUR = 0x704DDDE8;
    private static final int FACE_SIDES = 1;
    private static final int FACE_TOP = 1 << 1;
    private static final int FACE_BOTTOM = 1 << 2;
    private static final int GLASS_FACES = FACE_SIDES;
    private static final int TRIM_FACES = FACE_SIDES | FACE_TOP | FACE_BOTTOM;

    public GateCabinetRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
            GateCabinetBlockEntity cabinet,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay) {
        if (!cabinet.isPaired()) {
            return;
        }
        float closedAmount = 1.0F - cabinet.getRenderProgress(partialTick);
        if (closedAmount <= 0.001F) {
            return;
        }
        int width = cabinet.getGateWidth();
        float length = closedAmount * width * 0.5F;
        Direction facing = cabinet.getBlockState().getValue(GateCabinetBlock.FACING);
        Vector3f outward = new Vector3f(facing.getStepX(), 0.0F, facing.getStepZ());
        Vector3f sideways = new Vector3f(-facing.getStepZ(), 0.0F, facing.getStepX());

        Vector3f cabinetFace = new Vector3f(
                0.5F + outward.x() * 0.5F,
                0.0F,
                0.5F + outward.z() * 0.5F);
        Vector3f farFace = new Vector3f(
                cabinetFace.x() + outward.x() * length,
                0.0F,
                cabinetFace.z() + outward.z() * length);

        VertexConsumer glassBuffer = bufferSource.getBuffer(RenderType.entityTranslucent(GLASS_TEXTURE));
        renderCuboid(
                glassBuffer,
                poseStack.last(),
                cabinetFace,
                farFace,
                sideways,
                BARRIER_BOTTOM,
                BARRIER_TOP,
                HALF_THICKNESS,
                GLASS_COLOUR,
                GLASS_FACES,
                packedLight,
                packedOverlay);

        VertexConsumer trimBuffer = bufferSource.getBuffer(RenderType.entityTranslucent(TRIM_TEXTURE));
        float leadingEdgeLength = Math.min(TRIM_SIZE, length);
        Vector3f leadingEdgeStart = new Vector3f(
                farFace.x() - outward.x() * leadingEdgeLength,
                0.0F,
                farFace.z() - outward.z() * leadingEdgeLength);
        if (length > leadingEdgeLength) {
            renderCuboid(
                    trimBuffer,
                    poseStack.last(),
                    cabinetFace,
                    leadingEdgeStart,
                    sideways,
                    BARRIER_BOTTOM,
                    BARRIER_BOTTOM + TRIM_SIZE,
                    TRIM_HALF_THICKNESS,
                    TRIM_COLOUR,
                    TRIM_FACES,
                    packedLight,
                    packedOverlay);
            renderCuboid(
                    trimBuffer,
                    poseStack.last(),
                    cabinetFace,
                    leadingEdgeStart,
                    sideways,
                    BARRIER_TOP - TRIM_SIZE,
                    BARRIER_TOP,
                    TRIM_HALF_THICKNESS,
                    TRIM_COLOUR,
                    TRIM_FACES,
                    packedLight,
                    packedOverlay);
            renderCuboid(
                    trimBuffer,
                    poseStack.last(),
                    cabinetFace,
                    leadingEdgeStart,
                    sideways,
                    SAFETY_BAND_BOTTOM,
                    SAFETY_BAND_TOP,
                    TRIM_HALF_THICKNESS,
                    SAFETY_BAND_COLOUR,
                    TRIM_FACES,
                    packedLight,
                    packedOverlay);
        }
        renderCuboid(
                trimBuffer,
                poseStack.last(),
                leadingEdgeStart,
                farFace,
                sideways,
                BARRIER_BOTTOM,
                BARRIER_TOP,
                TRIM_HALF_THICKNESS,
                TRIM_COLOUR,
                TRIM_FACES,
                packedLight,
                packedOverlay);
    }

    private static void renderCuboid(
            VertexConsumer buffer,
            PoseStack.Pose pose,
            Vector3f near,
            Vector3f far,
            Vector3f sideways,
            float bottom,
            float top,
            float halfThickness,
            int colour,
            int faces,
            int packedLight,
            int packedOverlay) {
        Vector3f nlb = point(near, sideways, -halfThickness, bottom);
        Vector3f nrb = point(near, sideways, halfThickness, bottom);
        Vector3f nlt = point(near, sideways, -halfThickness, top);
        Vector3f nrt = point(near, sideways, halfThickness, top);
        Vector3f flb = point(far, sideways, -halfThickness, bottom);
        Vector3f frb = point(far, sideways, halfThickness, bottom);
        Vector3f flt = point(far, sideways, -halfThickness, top);
        Vector3f frt = point(far, sideways, halfThickness, top);

        if ((faces & FACE_SIDES) != 0) {
            quad(buffer, pose, sideways, colour, packedLight, packedOverlay, nrb, frb, frt, nrt);
            quad(buffer, pose, negate(sideways), colour, packedLight, packedOverlay, flb, nlb, nlt, flt);
        }
        if ((faces & FACE_TOP) != 0) {
            quad(buffer, pose, new Vector3f(0, 1, 0), colour, packedLight, packedOverlay, nrt, frt, flt, nlt);
        }
        if ((faces & FACE_BOTTOM) != 0) {
            quad(buffer, pose, new Vector3f(0, -1, 0), colour, packedLight, packedOverlay, nlb, flb, frb, nrb);
        }
    }

    private static Vector3f point(Vector3f base, Vector3f sideways, float side, float y) {
        return new Vector3f(base.x() + sideways.x() * side, y, base.z() + sideways.z() * side);
    }

    private static Vector3f negate(Vector3f vector) {
        return new Vector3f(-vector.x(), -vector.y(), -vector.z());
    }

    private static void quad(
            VertexConsumer buffer,
            PoseStack.Pose pose,
            Vector3f normal,
            int colour,
            int packedLight,
            int packedOverlay,
            Vector3f a,
            Vector3f b,
            Vector3f c,
            Vector3f d) {
        vertex(buffer, pose, normal, colour, packedLight, packedOverlay, a, 0, 1);
        vertex(buffer, pose, normal, colour, packedLight, packedOverlay, b, 1, 1);
        vertex(buffer, pose, normal, colour, packedLight, packedOverlay, c, 1, 0);
        vertex(buffer, pose, normal, colour, packedLight, packedOverlay, d, 0, 0);
    }

    private static void vertex(
            VertexConsumer buffer,
            PoseStack.Pose pose,
            Vector3f normal,
            int colour,
            int packedLight,
            int packedOverlay,
            Vector3f point,
            float u,
            float v) {
        buffer.addVertex(pose, point.x(), point.y(), point.z())
                .setColor(colour)
                .setUv(u, v)
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal(pose, normal.x(), normal.y(), normal.z());
    }

    @Override
    public AABB getRenderBoundingBox(GateCabinetBlockEntity cabinet) {
        Direction facing = cabinet.getBlockState().getValue(GateCabinetBlock.FACING);
        return new AABB(cabinet.getBlockPos()).expandTowards(
                facing.getStepX() * Math.max(1, cabinet.getGateWidth()),
                1.0,
                facing.getStepZ() * Math.max(1, cabinet.getGateWidth()));
    }
}
