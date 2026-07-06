package dev.wxdao.ccsmartcard.block.entity;

import dev.wxdao.ccsmartcard.block.ModBlockEntities;
import dev.wxdao.ccsmartcard.block.SmartCardReaderBlock;
import dev.wxdao.ccsmartcard.item.SmartCardItem;
import dev.wxdao.ccsmartcard.peripheral.SmartCardReaderPeripheral;
import dan200.computercraft.api.peripheral.AttachedComputerSet;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class SmartCardReaderBlockEntity extends BlockEntity {
    private final SmartCardReaderPeripheral peripheral = new SmartCardReaderPeripheral(this);
    private final AttachedComputerSet computers = new AttachedComputerSet();
    private ItemStack card = ItemStack.EMPTY;

    public SmartCardReaderBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.SMART_CARD_READER.get(), pos, blockState);
    }

    public IPeripheral getPeripheral(Direction side) {
        return peripheral;
    }

    public void attach(IComputerAccess computer) {
        computers.add(computer);
        if (hasCard()) {
            computer.queueEvent("smart_card_inserted", computer.getAttachmentName());
        }
    }

    public void detach(IComputerAccess computer) {
        computers.remove(computer);
    }

    public void cancelActiveInvocations() {
        peripheral.cancelAllInvocations();
    }

    public ItemStack getCard() {
        return card;
    }

    public boolean hasCard() {
        return !card.isEmpty();
    }

    public boolean insertCard(ItemStack stack) {
        return insertCard(stack, Direction.UP);
    }

    public boolean insertCard(ItemStack stack, Direction cardFace) {
        if (hasCard() || !SmartCardItem.isSmartCard(stack)) {
            return false;
        }
        card = stack.split(1);
        updateCardState(cardFace);
        queueCardEvent("smart_card_inserted");
        return true;
    }

    public ItemStack removeCard() {
        if (!hasCard()) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = card;
        card = ItemStack.EMPTY;
        updateCardState(null);
        queueCardEvent("smart_card_removed");
        return removed;
    }

    public ItemStack removeCardForBlockDestroy() {
        if (!hasCard()) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = card;
        card = ItemStack.EMPTY;
        setChanged();
        queueCardEvent("smart_card_removed");
        return removed;
    }

    public void updateCardMetadata() {
        setChanged();
    }

    private void updateCardState(Direction cardFace) {
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            if (state.hasProperty(SmartCardReaderBlock.HAS_CARD)) {
                BlockState updatedState = state.setValue(SmartCardReaderBlock.HAS_CARD, hasCard());
                if (hasCard() && cardFace != null && state.hasProperty(SmartCardReaderBlock.CARD_FACE)) {
                    updatedState = updatedState.setValue(SmartCardReaderBlock.CARD_FACE, cardFace);
                }
                if (updatedState != state) {
                    level.setBlock(worldPosition, updatedState, 3);
                }
            }
        }
    }

    private void queueCardEvent(String event) {
        computers.forEach(computer -> computer.queueEvent(event, computer.getAttachmentName()));
    }

    public boolean isOnline() {
        return level != null && !level.isClientSide;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        card = ItemStack.parseOptional(registries, tag.getCompound("Card"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Card", card.saveOptional(registries));
    }
}
