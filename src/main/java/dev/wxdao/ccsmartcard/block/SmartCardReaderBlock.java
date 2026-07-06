package dev.wxdao.ccsmartcard.block;

import dev.wxdao.ccsmartcard.block.entity.SmartCardReaderBlockEntity;
import dev.wxdao.ccsmartcard.item.SmartCardItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class SmartCardReaderBlock extends BaseEntityBlock implements EntityBlock {
    public static final BooleanProperty HAS_CARD = BooleanProperty.create("has_card");
    public static final DirectionProperty CARD_FACE = DirectionProperty.create("card_face");

    public SmartCardReaderBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(HAS_CARD, false)
                .setValue(CARD_FACE, Direction.UP));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SmartCardReaderBlockEntity(pos, state);
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(SmartCardReaderBlock::new);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof SmartCardReaderBlockEntity reader)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (reader.hasCard()) {
            if (!level.isClientSide) {
                giveCardToPlayer(player, reader.removeCard());
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (SmartCardItem.isSmartCard(stack)) {
            if (level.isClientSide) {
                return ItemInteractionResult.SUCCESS;
            }
            reader.insertCard(stack, hitResult.getDirection());
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof SmartCardReaderBlockEntity reader) || !reader.hasCard()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            giveCardToPlayer(player, reader.removeCard());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void giveCardToPlayer(Player player, ItemStack card) {
        if (!player.getInventory().add(card)) {
            player.drop(card, false);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SmartCardReaderBlockEntity reader) {
            reader.cancelActiveInvocations();
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), reader.removeCardForBlockDestroy());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(HAS_CARD, CARD_FACE);
    }

    @Override
    protected boolean triggerEvent(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, int id, int param) {
        super.triggerEvent(state, level, pos, id, param);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity != null && blockEntity.triggerEvent(id, param);
    }
}
