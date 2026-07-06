package dev.wxdao.ccsmartcard.block.entity;

import dev.wxdao.ccsmartcard.block.FingerprintScannerBlock;
import dev.wxdao.ccsmartcard.block.ModBlockEntities;
import dev.wxdao.ccsmartcard.peripheral.FingerprintScannerPeripheral;
import dan200.computercraft.api.peripheral.AttachedComputerSet;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class FingerprintScannerBlockEntity extends BlockEntity {
    private final FingerprintScannerPeripheral peripheral = new FingerprintScannerPeripheral(this);
    private final AttachedComputerSet computers = new AttachedComputerSet();
    private long litUntilTick;

    public FingerprintScannerBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.FINGERPRINT_SCANNER.get(), pos, blockState);
    }

    public IPeripheral getPeripheral(Direction side) {
        return peripheral;
    }

    public void attach(IComputerAccess computer) {
        computers.add(computer);
    }

    public void detach(IComputerAccess computer) {
        peripheral.cancelPendingScans(computer);
        computers.remove(computer);
    }

    public void scan(Player player, Direction face) {
        activateLight(face);
        peripheral.completeScan(player);
    }

    public void cancelPendingScans() {
        peripheral.cancelPendingScans();
    }

    public void queueEvent(String event, Object... arguments) {
        computers.forEach(computer -> computer.queueEvent(event, arguments));
    }

    public void queueEvent(IComputerAccess computer, String event, Object... arguments) {
        computer.queueEvent(event, arguments);
    }

    private void activateLight(Direction face) {
        Level level = getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        litUntilTick = level.getGameTime() + 10;
        BlockState state = getBlockState();
        if (state.hasProperty(FingerprintScannerBlock.LIT)) {
            level.setBlock(worldPosition, state
                    .setValue(FingerprintScannerBlock.LIT, true)
                    .setValue(FingerprintScannerBlock.LIT_FACE, face), 3);
        }
        level.scheduleTick(worldPosition, getBlockState().getBlock(), 10);
        setChanged();
    }

    public void tickLight() {
        Level level = getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        long remainingTicks = litUntilTick - level.getGameTime();
        if (remainingTicks > 0) {
            level.scheduleTick(worldPosition, getBlockState().getBlock(), (int) remainingTicks);
            return;
        }
        clearLight();
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (!level.isClientSide) {
            clearLight();
        }
    }

    private void clearLight() {
        Level level = getLevel();
        if (level == null) {
            return;
        }
        BlockState state = getBlockState();
        if (state.hasProperty(FingerprintScannerBlock.LIT) && state.getValue(FingerprintScannerBlock.LIT)) {
            level.setBlock(worldPosition, state.setValue(FingerprintScannerBlock.LIT, false), 3);
        }
        litUntilTick = 0;
        setChanged();
    }
}
