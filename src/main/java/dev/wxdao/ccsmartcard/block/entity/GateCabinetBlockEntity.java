package dev.wxdao.ccsmartcard.block.entity;

import dan200.computercraft.api.peripheral.AttachedComputerSet;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dev.wxdao.ccsmartcard.block.GateBarrierBlock;
import dev.wxdao.ccsmartcard.block.GateCabinetBlock;
import dev.wxdao.ccsmartcard.block.GateIndicator;
import dev.wxdao.ccsmartcard.block.ModBlockEntities;
import dev.wxdao.ccsmartcard.block.ModBlocks;
import dev.wxdao.ccsmartcard.peripheral.AccessGatePeripheral;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/** Owns one cabinet and, on the canonical side, the shared Access Gate state machine. */
public final class GateCabinetBlockEntity extends BlockEntity {
    public enum GateState {
        CLOSED("closed"),
        OPENING("opening"),
        OPEN("open"),
        CLOSING("closing"),
        OBSTRUCTED("obstructed"),
        UNPAIRED("unpaired");

        private final String serializedName;

        GateState(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }
    }

    private static final int MOVEMENT_TICKS = 10;
    private static final int FAIL_OPEN_GRACE_TICKS = 20;
    private static final float STEP = 1.0F / MOVEMENT_TICKS;

    private final AccessGatePeripheral peripheral = new AccessGatePeripheral(this);
    private final AttachedComputerSet computers = new AttachedComputerSet();

    @Nullable
    private BlockPos partnerPos;
    private int width;
    private float progress = 1.0F;
    private float previousProgress = 1.0F;
    private boolean targetOpen = true;
    private GateState reportedState = GateState.UNPAIRED;
    private int obstructedTicks;
    private long stateModifiedTick;
    private int stateModifiedSequence;
    private int failOpenGraceTicks = FAIL_OPEN_GRACE_TICKS;
    private int pairSearchDelay;

    public GateCabinetBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.GATE_CABINET.get(), pos, blockState);
    }

    public IPeripheral getPeripheral(Direction side) {
        return peripheral;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GateCabinetBlockEntity cabinet) {
        cabinet.tickServer();
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, GateCabinetBlockEntity cabinet) {
        cabinet.previousProgress = cabinet.progress;
    }

    private void tickServer() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        previousProgress = progress;
        if (!validatePair()) {
            if (pairSearchDelay > 0) {
                pairSearchDelay--;
            } else {
                findAndCreatePair();
                pairSearchDelay = 5;
            }
        }

        reconcileLoadedPairState();
        GateCabinetBlockEntity controller = controller();
        if (controller != this) {
            return;
        }
        if (partnerPos == null) {
            updateReportedState(GateState.UNPAIRED);
            updateIndicator(GateIndicator.AMBER);
            return;
        }

        tickFailOpen();
        boolean sharedStateChanged = false;
        if (obstructedTicks > 0) {
            obstructedTicks--;
            sharedStateChanged = true;
        }
        if (!targetOpen && progress > 0.0F && isObstructed(serverLevel)) {
            targetOpen = true;
            obstructedTicks = 2;
            sharedStateChanged = true;
            updateReportedState(GateState.OBSTRUCTED);
            queueGateEvent("access_gate_obstructed", getGateId());
        }

        float oldProgress = progress;
        if (targetOpen && progress < 1.0F) {
            progress = Math.min(1.0F, progress + STEP);
        } else if (!targetOpen && progress > 0.0F) {
            progress = Math.max(0.0F, progress - STEP);
        }
        sharedStateChanged |= oldProgress != progress;
        if (sharedStateChanged) {
            markSharedStateChanged();
        }

        GateState state = currentMotionState();
        updateReportedState(state);
        updateIndicator(indicatorFor(state));
        updateBarriers(progress <= 0.0F);
        if (sharedStateChanged) {
            syncPair();
        }
    }

    private void tickFailOpen() {
        if (hasAnyComputers()) {
            failOpenGraceTicks = -1;
            return;
        }
        if (failOpenGraceTicks < 0) {
            failOpenGraceTicks = FAIL_OPEN_GRACE_TICKS;
        } else if (failOpenGraceTicks > 0) {
            failOpenGraceTicks--;
        } else if (!targetOpen) {
            setTargetOpen(true);
        }
    }

    private boolean validatePair() {
        if (partnerPos == null || level == null) {
            return false;
        }
        if (!level.isLoaded(partnerPos)) {
            return true;
        }
        if (!(level.getBlockEntity(partnerPos) instanceof GateCabinetBlockEntity partner)
                || !worldPosition.equals(partner.partnerPos)
                || !facingsOppose(partner)
                || distanceTo(partnerPos) != width + 1) {
            clearPair(true);
            return false;
        }
        return true;
    }

    private void findAndCreatePair() {
        if (level == null || partnerPos != null || !getBlockState().hasProperty(GateCabinetBlock.FACING)) {
            return;
        }
        Direction facing = getBlockState().getValue(GateCabinetBlock.FACING);
        GateCabinetBlockEntity candidate = null;
        int candidateWidth = 0;
        for (int possibleWidth = 1; possibleWidth <= 2; possibleWidth++) {
            BlockPos possiblePos = worldPosition.relative(facing, possibleWidth + 1);
            if (!level.isLoaded(possiblePos)) {
                continue;
            }
            if (level.getBlockEntity(possiblePos) instanceof GateCabinetBlockEntity other
                    && (other.partnerPos == null || worldPosition.equals(other.partnerPos))
                    && facingsOppose(other)
                    && clearForPair(facing, possibleWidth)) {
                if (candidate != null) {
                    return;
                }
                candidate = other;
                candidateWidth = possibleWidth;
            }
        }
        if (candidate == null) {
            return;
        }
        establishPair(candidate, candidateWidth);
    }

    private boolean clearForPair(Direction facing, int possibleWidth) {
        for (int offset = 1; offset <= possibleWidth; offset++) {
            BlockPos pos = worldPosition.relative(facing, offset);
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && !state.canBeReplaced() && !state.is(ModBlocks.GATE_BARRIER.get())) {
                return false;
            }
        }
        return true;
    }

    private boolean facingsOppose(GateCabinetBlockEntity other) {
        BlockState ownState = getBlockState();
        BlockState otherState = other.getBlockState();
        return ownState.hasProperty(GateCabinetBlock.FACING)
                && otherState.hasProperty(GateCabinetBlock.FACING)
                && otherState.getValue(GateCabinetBlock.FACING) == ownState.getValue(GateCabinetBlock.FACING).getOpposite();
    }

    private int distanceTo(BlockPos pos) {
        return Math.abs(pos.getX() - worldPosition.getX())
                + Math.abs(pos.getY() - worldPosition.getY())
                + Math.abs(pos.getZ() - worldPosition.getZ());
    }

    private void establishPair(GateCabinetBlockEntity other, int gateWidth) {
        partnerPos = other.worldPosition.immutable();
        other.partnerPos = worldPosition.immutable();
        width = gateWidth;
        other.width = gateWidth;

        GateCabinetBlockEntity controller = controller();
        controller.progress = 1.0F;
        controller.previousProgress = 1.0F;
        controller.targetOpen = true;
        controller.obstructedTicks = 0;
        controller.failOpenGraceTicks = FAIL_OPEN_GRACE_TICKS;
        controller.reportedState = GateState.UNPAIRED;
        controller.markSharedStateChanged();
        controller.updateBarriers(false);
        controller.updateReportedState(GateState.OPEN);
        controller.updateIndicator(GateIndicator.GREEN);
        controller.syncPair();
    }

    public void findPairSoon() {
        pairSearchDelay = 0;
        setChanged();
    }

    public void onCabinetRemoved() {
        if (level == null || level.isClientSide || partnerPos == null) {
            return;
        }
        BlockPos oldPartner = partnerPos;
        removeManagedBarriers(oldPartner);
        if (level.isLoaded(oldPartner)
                && level.getBlockEntity(oldPartner) instanceof GateCabinetBlockEntity partner
                && worldPosition.equals(partner.partnerPos)) {
            partner.partnerPos = null;
            partner.width = 0;
            partner.progress = 1.0F;
            partner.targetOpen = true;
            partner.obstructedTicks = 0;
            partner.updateReportedState(GateState.UNPAIRED);
            partner.updateIndicator(GateIndicator.AMBER);
            partner.syncSelf();
        }
        partnerPos = null;
        width = 0;
    }

    private void clearPair(boolean removeBarriers) {
        BlockPos oldPartner = partnerPos;
        if (oldPartner == null) {
            return;
        }
        if (removeBarriers) {
            removeManagedBarriers(oldPartner);
        }
        partnerPos = null;
        width = 0;
        progress = 1.0F;
        targetOpen = true;
        obstructedTicks = 0;
        updateReportedState(GateState.UNPAIRED);
        updateIndicator(GateIndicator.AMBER);
        syncSelf();
    }

    private void removeManagedBarriers(BlockPos otherPos) {
        if (level == null) {
            return;
        }
        Direction direction = directionTo(otherPos);
        int distance = distanceTo(otherPos);
        for (int offset = 1; offset < distance; offset++) {
            BlockPos barrierPos = worldPosition.relative(direction, offset);
            if (level.getBlockState(barrierPos).is(ModBlocks.GATE_BARRIER.get())) {
                level.removeBlock(barrierPos, false);
            }
        }
    }

    private void updateBarriers(boolean extended) {
        if (level == null || partnerPos == null || controller() != this) {
            return;
        }
        Direction direction = directionTo(partnerPos);
        for (int offset = 1; offset <= width; offset++) {
            BlockPos barrierPos = worldPosition.relative(direction, offset);
            BlockState existing = level.getBlockState(barrierPos);
            if (!existing.isAir() && !existing.canBeReplaced() && !existing.is(ModBlocks.GATE_BARRIER.get())) {
                clearPair(true);
                return;
            }
            BlockState wanted = ModBlocks.GATE_BARRIER.get().defaultBlockState()
                    .setValue(GateBarrierBlock.AXIS, direction.getAxis())
                    .setValue(GateBarrierBlock.EXTENDED, extended);
            if (existing != wanted) {
                level.setBlock(barrierPos, wanted, 3);
            }
        }
    }

    private boolean isObstructed(ServerLevel serverLevel) {
        if (partnerPos == null) {
            return false;
        }
        Direction direction = directionTo(partnerPos);
        BlockPos first = worldPosition.relative(direction);
        BlockPos last = worldPosition.relative(direction, width);
        AABB sweep = new AABB(first).minmax(new AABB(last)).inflate(0.0, 0.5, 0.125);
        List<Entity> entities = serverLevel.getEntities((Entity) null, sweep, GateCabinetBlockEntity::isGateObstruction);
        return !entities.isEmpty();
    }

    private static boolean isGateObstruction(Entity entity) {
        if (entity.isSpectator()) {
            return false;
        }
        return entity instanceof LivingEntity || entity instanceof VehicleEntity;
    }

    private Direction directionTo(BlockPos pos) {
        int dx = Integer.signum(pos.getX() - worldPosition.getX());
        int dz = Integer.signum(pos.getZ() - worldPosition.getZ());
        return Direction.fromDelta(dx, 0, dz);
    }

    @Nullable
    private GateCabinetBlockEntity partner() {
        if (level == null || partnerPos == null || !level.isLoaded(partnerPos)) {
            return null;
        }
        return level.getBlockEntity(partnerPos) instanceof GateCabinetBlockEntity partner ? partner : null;
    }

    private GateCabinetBlockEntity controller() {
        GateCabinetBlockEntity partner = partner();
        if (partner == null) {
            return this;
        }
        return worldPosition.compareTo(partner.worldPosition) <= 0 ? this : partner;
    }

    /**
     * Resolves the duplicated gate state after both cabinet chunks become loaded again. State timestamps use the
     * persisted world game time, with a per-tick sequence for multiple changes in one tick. A fail-open state wins
     * the otherwise pathological equal-version conflict, matching the gate's safety policy.
     */
    private void reconcileLoadedPairState() {
        GateCabinetBlockEntity partner = partner();
        if (partner == null) {
            return;
        }

        GateCabinetBlockEntity preferred = preferredStateSource(this, partner);
        GateCabinetBlockEntity other = preferred == this ? partner : this;
        if (!other.sharedStateEquals(preferred)) {
            other.copySharedStateFrom(preferred);
            other.syncSelf();
        }
    }

    private static GateCabinetBlockEntity preferredStateSource(
            GateCabinetBlockEntity first, GateCabinetBlockEntity second) {
        int tickComparison = Long.compare(first.stateModifiedTick, second.stateModifiedTick);
        if (tickComparison != 0) {
            return tickComparison > 0 ? first : second;
        }
        int sequenceComparison = Integer.compare(first.stateModifiedSequence, second.stateModifiedSequence);
        if (sequenceComparison != 0) {
            return sequenceComparison > 0 ? first : second;
        }
        if (first.targetOpen != second.targetOpen) {
            return first.targetOpen ? first : second;
        }
        int progressComparison = Float.compare(first.progress, second.progress);
        if (progressComparison != 0) {
            return progressComparison > 0 ? first : second;
        }
        return first.worldPosition.compareTo(second.worldPosition) <= 0 ? first : second;
    }

    private boolean sharedStateEquals(GateCabinetBlockEntity other) {
        return Float.compare(progress, other.progress) == 0
                && targetOpen == other.targetOpen
                && obstructedTicks == other.obstructedTicks
                && stateModifiedTick == other.stateModifiedTick
                && stateModifiedSequence == other.stateModifiedSequence;
    }

    private void copySharedStateFrom(GateCabinetBlockEntity source) {
        progress = source.progress;
        previousProgress = source.progress;
        targetOpen = source.targetOpen;
        obstructedTicks = source.obstructedTicks;
        reportedState = source.reportedState;
        stateModifiedTick = source.stateModifiedTick;
        stateModifiedSequence = source.stateModifiedSequence;
    }

    private void markSharedStateChanged() {
        long gameTime = level == null ? stateModifiedTick : level.getGameTime();
        if (gameTime > stateModifiedTick) {
            stateModifiedTick = gameTime;
            stateModifiedSequence = 0;
        } else {
            stateModifiedSequence++;
        }
    }

    public void attach(IComputerAccess computer) {
        computers.add(computer);
        runOnServer(() -> controller().failOpenGraceTicks = -1);
    }

    public void detach(IComputerAccess computer) {
        computers.remove(computer);
        runOnServer(() -> {
            GateCabinetBlockEntity controller = controller();
            if (!controller.hasAnyComputers()) {
                controller.failOpenGraceTicks = FAIL_OPEN_GRACE_TICKS;
            }
        });
    }

    private void runOnServer(Runnable task) {
        if (level == null || level.getServer() == null) {
            return;
        }
        level.getServer().execute(task);
    }

    private boolean hasAnyComputers() {
        if (computers.hasComputers()) {
            return true;
        }
        GateCabinetBlockEntity partner = partner();
        return partner != null && partner.computers.hasComputers();
    }

    public boolean setTargetOpen(boolean open) {
        GateCabinetBlockEntity controller = controller();
        if (controller.partnerPos == null) {
            return false;
        }
        GateState oldState = controller.currentMotionState();
        if (controller.targetOpen != open || controller.obstructedTicks > 0) {
            controller.targetOpen = open;
            controller.obstructedTicks = 0;
            controller.markSharedStateChanged();
        }
        controller.updateReportedState(controller.currentMotionState(), oldState == controller.currentMotionState());
        controller.updateIndicator(indicatorFor(controller.currentMotionState()));
        controller.syncPair();
        return true;
    }

    @Nullable
    public String getGateId() {
        GateCabinetBlockEntity controller = controller();
        if (controller.partnerPos == null || controller.level == null) {
            return null;
        }
        BlockPos first = controller.worldPosition;
        BlockPos second = controller.partnerPos;
        if (first.compareTo(second) > 0) {
            BlockPos swap = first;
            first = second;
            second = swap;
        }
        String material = controller.level.dimension().location() + "|" + first.toShortString() + "|" + second.toShortString();
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 12);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public String getGateState() {
        GateCabinetBlockEntity controller = controller();
        return (controller.partnerPos == null ? GateState.UNPAIRED : controller.currentMotionState()).serializedName();
    }

    public int getGateWidth() {
        GateCabinetBlockEntity controller = controller();
        return controller.partnerPos == null ? 0 : controller.width;
    }

    public float getRenderProgress(float partialTick) {
        GateCabinetBlockEntity controller = controller();
        return Mth.lerp(partialTick, controller.previousProgress, controller.progress);
    }

    public boolean isPaired() {
        return controller().partnerPos != null;
    }

    private GateState currentMotionState() {
        if (partnerPos == null) {
            return GateState.UNPAIRED;
        }
        if (obstructedTicks > 0) {
            return GateState.OBSTRUCTED;
        }
        if (progress <= 0.0F && !targetOpen) {
            return GateState.CLOSED;
        }
        if (progress >= 1.0F && targetOpen) {
            return GateState.OPEN;
        }
        return targetOpen ? GateState.OPENING : GateState.CLOSING;
    }

    private static GateIndicator indicatorFor(GateState state) {
        return switch (state) {
            case CLOSED -> GateIndicator.RED;
            case OPEN -> GateIndicator.GREEN;
            default -> GateIndicator.AMBER;
        };
    }

    private void updateReportedState(GateState nextState) {
        updateReportedState(nextState, false);
    }

    private void updateReportedState(GateState nextState, boolean suppressEvent) {
        if (reportedState == nextState) {
            return;
        }
        GateState oldState = reportedState;
        reportedState = nextState;
        if (!suppressEvent && level != null && !level.isClientSide && partnerPos != null) {
            queueGateEvent("access_gate_state_changed", getGateId(), oldState.serializedName(), nextState.serializedName());
        }
    }

    private void updateIndicator(GateIndicator indicator) {
        updateCabinetIndicator(this, indicator);
        GateCabinetBlockEntity partner = partner();
        if (partner != null) {
            updateCabinetIndicator(partner, indicator);
        }
    }

    private static void updateCabinetIndicator(GateCabinetBlockEntity cabinet, GateIndicator indicator) {
        if (cabinet.level == null) {
            return;
        }
        BlockState state = cabinet.getBlockState();
        if (state.hasProperty(GateCabinetBlock.INDICATOR) && state.getValue(GateCabinetBlock.INDICATOR) != indicator) {
            cabinet.level.setBlock(cabinet.worldPosition, state.setValue(GateCabinetBlock.INDICATOR, indicator), 3);
        }
    }

    private void queueGateEvent(String event, Object... arguments) {
        Set<Integer> notifiedComputerIds = new HashSet<>();
        computers.forEach(computer -> {
            if (notifiedComputerIds.add(computer.getID())) {
                computer.queueEvent(event, arguments);
            }
        });
        GateCabinetBlockEntity partner = partner();
        if (partner != null) {
            partner.computers.forEach(computer -> {
                if (notifiedComputerIds.add(computer.getID())) {
                    computer.queueEvent(event, arguments);
                }
            });
        }
    }

    private void syncPair() {
        syncSelf();
        GateCabinetBlockEntity partner = partner();
        if (partner != null) {
            partner.partnerPos = worldPosition.immutable();
            partner.width = width;
            partner.progress = progress;
            partner.targetOpen = targetOpen;
            partner.obstructedTicks = obstructedTicks;
            partner.reportedState = reportedState;
            partner.stateModifiedTick = stateModifiedTick;
            partner.stateModifiedSequence = stateModifiedSequence;
            partner.syncSelf();
        }
    }

    private void syncSelf() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        float oldProgress = progress;
        partnerPos = tag.contains("Partner") ? BlockPos.of(tag.getLong("Partner")) : null;
        width = tag.getInt("Width");
        progress = Mth.clamp(tag.getFloat("Progress"), 0.0F, 1.0F);
        previousProgress = level != null && level.isClientSide ? oldProgress : progress;
        targetOpen = tag.getBoolean("TargetOpen");
        obstructedTicks = tag.getInt("ObstructedTicks");
        stateModifiedTick = tag.getLong("StateModifiedTick");
        stateModifiedSequence = tag.getInt("StateModifiedSequence");
        reportedState = partnerPos == null ? GateState.UNPAIRED : currentMotionState();
        failOpenGraceTicks = FAIL_OPEN_GRACE_TICKS;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (partnerPos != null) {
            tag.putLong("Partner", partnerPos.asLong());
        }
        tag.putInt("Width", width);
        tag.putFloat("Progress", progress);
        tag.putBoolean("TargetOpen", targetOpen);
        tag.putInt("ObstructedTicks", obstructedTicks);
        tag.putLong("StateModifiedTick", stateModifiedTick);
        tag.putInt("StateModifiedSequence", stateModifiedSequence);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
