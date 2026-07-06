package dev.wxdao.ccsmartcard.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.wxdao.ccsmartcard.CCSmartCard;
import dev.wxdao.ccsmartcard.block.SmartCardReaderBlock;
import dev.wxdao.ccsmartcard.block.entity.SmartCardReaderBlockEntity;
import dev.wxdao.ccsmartcard.item.CardColours;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

public final class SmartCardReaderRenderer implements BlockEntityRenderer<SmartCardReaderBlockEntity> {
    private static final ResourceLocation CARD_BODY_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CCSmartCard.MOD_ID, "textures/block/smart_card_reader_card_body.png");
    private static final ResourceLocation CARD_CHIP_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CCSmartCard.MOD_ID, "textures/block/smart_card_reader_card_chip.png");

    private static final float HALF_LENGTH = 6.0f / 16.0f;
    private static final float HALF_WIDTH = 3.0f / 16.0f;
    private static final float HALF_THICKNESS = 0.25f / 16.0f;
    private static final float CHIP_LIFT = 0.01f / 16.0f;
    private static final float EDGE_U_MIN = 2.0f / 16.0f;
    private static final float EDGE_U_MAX = 14.0f / 16.0f;
    private static final float SIDE_U_MIN = 4.0f / 16.0f;
    private static final float SIDE_U_MAX = 12.0f / 16.0f;
    private static final float EDGE_V_MIN = 14.0f / 16.0f;
    private static final float EDGE_V_MAX = 15.0f / 16.0f;
    private static final int WHITE = 0xFFFFFFFF;

    public SmartCardReaderRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
            SmartCardReaderBlockEntity reader,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay) {
        ItemStack card = reader.getCard();
        if (card.isEmpty()) {
            return;
        }

        Direction face = reader.getBlockState().getValue(SmartCardReaderBlock.CARD_FACE);
        int cardLight = reader.getLevel() == null
                ? packedLight
                : LevelRenderer.getLightColor(reader.getLevel(), reader.getBlockPos().relative(face));
        Basis basis = Basis.forFace(face);
        Vector3f center = point(
                new Vector3f(0.5f, 0.5f, 0.5f),
                basis.normal,
                0.5f + HALF_THICKNESS,
                basis.right,
                0.0f,
                basis.up,
                0.0f);

        PoseStack.Pose pose = poseStack.last();
        int bodyColour = opaque(CardColours.getCardBodyColour(card));
        VertexConsumer bodyBuffer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(CARD_BODY_TEXTURE));
        renderBody(bodyBuffer, pose, basis, center, bodyColour, cardLight, packedOverlay);

        VertexConsumer chipBuffer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(CARD_CHIP_TEXTURE));
        renderChip(chipBuffer, pose, basis, center, cardLight, packedOverlay);
    }

    private static void renderBody(
            VertexConsumer buffer,
            PoseStack.Pose pose,
            Basis basis,
            Vector3f center,
            int colour,
            int packedLight,
            int packedOverlay) {
        Vector3f frontCenter = point(center, basis.normal, HALF_THICKNESS, basis.right, 0.0f, basis.up, 0.0f);
        Vector3f frontMinMin = point(frontCenter, basis.normal, 0.0f, basis.right, -HALF_LENGTH, basis.up, -HALF_WIDTH);
        Vector3f frontMaxMin = point(frontCenter, basis.normal, 0.0f, basis.right, HALF_LENGTH, basis.up, -HALF_WIDTH);
        Vector3f frontMaxMax = point(frontCenter, basis.normal, 0.0f, basis.right, HALF_LENGTH, basis.up, HALF_WIDTH);
        Vector3f frontMinMax = point(frontCenter, basis.normal, 0.0f, basis.right, -HALF_LENGTH, basis.up, HALF_WIDTH);

        Vector3f backMinMin = point(center, basis.normal, -HALF_THICKNESS, basis.right, -HALF_LENGTH, basis.up, -HALF_WIDTH);
        Vector3f backMaxMin = point(center, basis.normal, -HALF_THICKNESS, basis.right, HALF_LENGTH, basis.up, -HALF_WIDTH);
        Vector3f backMaxMax = point(center, basis.normal, -HALF_THICKNESS, basis.right, HALF_LENGTH, basis.up, HALF_WIDTH);
        Vector3f backMinMax = point(center, basis.normal, -HALF_THICKNESS, basis.right, -HALF_LENGTH, basis.up, HALF_WIDTH);

        quad(buffer, pose, basis.normal, colour, packedLight, packedOverlay, frontMinMin, frontMaxMin, frontMaxMax, frontMinMax);

        int edgeColour = shade(colour, 0.62f);
        quad(buffer, pose, negate(basis.up), edgeColour, packedLight, packedOverlay, backMinMin, backMaxMin, frontMaxMin, frontMinMin,
                EDGE_U_MIN, EDGE_V_MAX, EDGE_U_MAX, EDGE_V_MIN);
        quad(buffer, pose, basis.up, edgeColour, packedLight, packedOverlay, backMaxMax, backMinMax, frontMinMax, frontMaxMax,
                EDGE_U_MIN, EDGE_V_MAX, EDGE_U_MAX, EDGE_V_MIN);
        quad(buffer, pose, negate(basis.right), edgeColour, packedLight, packedOverlay, backMinMax, backMinMin, frontMinMin, frontMinMax,
                SIDE_U_MIN, EDGE_V_MAX, SIDE_U_MAX, EDGE_V_MIN);
        quad(buffer, pose, basis.right, edgeColour, packedLight, packedOverlay, backMaxMin, backMaxMax, frontMaxMax, frontMaxMin,
                SIDE_U_MIN, EDGE_V_MAX, SIDE_U_MAX, EDGE_V_MIN);
    }

    private static void renderChip(
            VertexConsumer buffer,
            PoseStack.Pose pose,
            Basis basis,
            Vector3f center,
            int packedLight,
            int packedOverlay) {
        Vector3f chipCenter = point(center, basis.normal, HALF_THICKNESS + CHIP_LIFT, basis.right, 0.0f, basis.up, 0.0f);

        Vector3f minMin = point(chipCenter, basis.normal, 0.0f, basis.right, -HALF_LENGTH, basis.up, -HALF_WIDTH);
        Vector3f maxMin = point(chipCenter, basis.normal, 0.0f, basis.right, HALF_LENGTH, basis.up, -HALF_WIDTH);
        Vector3f maxMax = point(chipCenter, basis.normal, 0.0f, basis.right, HALF_LENGTH, basis.up, HALF_WIDTH);
        Vector3f minMax = point(chipCenter, basis.normal, 0.0f, basis.right, -HALF_LENGTH, basis.up, HALF_WIDTH);
        quad(buffer, pose, basis.normal, WHITE, packedLight, packedOverlay, minMin, maxMin, maxMax, minMax);
    }

    private static Vector3f point(
            Vector3f center,
            Vector3f normal,
            float normalScale,
            Vector3f right,
            float rightScale,
            Vector3f up,
            float upScale) {
        return new Vector3f(
                center.x() + normal.x() * normalScale + right.x() * rightScale + up.x() * upScale,
                center.y() + normal.y() * normalScale + right.y() * rightScale + up.y() * upScale,
                center.z() + normal.z() * normalScale + right.z() * rightScale + up.z() * upScale);
    }

    private static Vector3f negate(Vector3f vector) {
        return new Vector3f(-vector.x(), -vector.y(), -vector.z());
    }

    private static int opaque(int colour) {
        return 0xFF000000 | (colour & 0xFFFFFF);
    }

    private static int shade(int colour, float multiplier) {
        int red = Math.round(((colour >> 16) & 0xFF) * multiplier);
        int green = Math.round(((colour >> 8) & 0xFF) * multiplier);
        int blue = Math.round((colour & 0xFF) * multiplier);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
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
        quad(buffer, pose, normal, colour, packedLight, packedOverlay, a, b, c, d, 0.0f, 1.0f, 1.0f, 0.0f);
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
            Vector3f d,
            float minU,
            float minV,
            float maxU,
            float maxV) {
        vertex(buffer, pose, normal, colour, packedLight, packedOverlay, a, minU, minV);
        vertex(buffer, pose, normal, colour, packedLight, packedOverlay, b, maxU, minV);
        vertex(buffer, pose, normal, colour, packedLight, packedOverlay, c, maxU, maxV);
        vertex(buffer, pose, normal, colour, packedLight, packedOverlay, d, minU, maxV);
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

    private record Basis(Vector3f normal, Vector3f right, Vector3f up) {
        private static Basis forFace(Direction face) {
            Vector3f normal = new Vector3f(face.getStepX(), face.getStepY(), face.getStepZ());
            return switch (face.getAxis()) {
                case Y -> new Basis(normal, new Vector3f(1.0f, 0.0f, 0.0f), new Vector3f(0.0f, 0.0f, 1.0f));
                case Z -> new Basis(normal, new Vector3f(1.0f, 0.0f, 0.0f), new Vector3f(0.0f, 1.0f, 0.0f));
                case X -> new Basis(normal, new Vector3f(0.0f, 0.0f, 1.0f), new Vector3f(0.0f, 1.0f, 0.0f));
            };
        }
    }
}
