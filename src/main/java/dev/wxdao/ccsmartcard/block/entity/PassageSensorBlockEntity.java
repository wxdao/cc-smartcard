package dev.wxdao.ccsmartcard.block.entity;

import dev.wxdao.ccsmartcard.block.ModBlockEntities;
import dev.wxdao.ccsmartcard.peripheral.PassageSensorPeripheral;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.AttachedComputerSet;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PassageSensorBlockEntity extends BlockEntity {
    public static final String ENTERED_EVENT = "passage_entity_entered";
    public static final String LEFT_EVENT = "passage_entity_left";
    public static final String RESET_EVENT = "passage_sensor_reset";

    private static final int MAX_BOUNDARY_DISTANCE = 8;
    private static final int MAX_VOLUME = 256;
    private static final String AREA_MIN_TAG = "AreaMin";
    private static final String AREA_MAX_TAG = "AreaMax";
    private static final BlockPos DEFAULT_MINIMUM = new BlockPos(0, -3, 0);
    private static final BlockPos DEFAULT_MAXIMUM = new BlockPos(0, -1, 0);

    private final PassageSensorPeripheral peripheral = new PassageSensorPeripheral(this);
    private final AttachedComputerSet computers = new AttachedComputerSet();
    private final Set<IComputerAccess> attachedComputers = ConcurrentHashMap.newKeySet();

    private BlockPos areaMinimum = DEFAULT_MINIMUM;
    private BlockPos areaMaximum = DEFAULT_MAXIMUM;
    private Map<Entity, Observation> observations = new IdentityHashMap<>();
    private boolean sessionActive;

    public PassageSensorBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.PASSAGE_SENSOR.get(), pos, blockState);
    }

    public IPeripheral getPeripheral(Direction side) {
        return peripheral;
    }

    public void attach(IComputerAccess computer) {
        attachedComputers.add(computer);
        computers.add(computer);
        runOnServer(() -> {
            if (!attachedComputers.contains(computer) || isRemoved()) {
                return;
            }
            if (!sessionActive) {
                beginSession();
            } else {
                scanAndQueueChanges(computer);
            }
            for (Observation observation : sortedObservations()) {
                queueEntered(computer, observation);
            }
        });
    }

    public void detach(IComputerAccess computer) {
        attachedComputers.remove(computer);
        computers.remove(computer);
        runOnServer(() -> {
            if (attachedComputers.isEmpty()) {
                resetSession(false);
            }
        });
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PassageSensorBlockEntity sensor) {
        if (sensor.attachedComputers.isEmpty()) {
            if (sensor.sessionActive) {
                sensor.resetSession(false);
            }
            return;
        }
        if (!sensor.sessionActive) {
            sensor.beginSession();
        } else {
            sensor.scanAndQueueChanges();
        }
    }

    public Map<String, Object> getArea() {
        Map<String, Object> area = new LinkedHashMap<>();
        area.put("minimum", coordinateMap(areaMinimum));
        area.put("maximum", coordinateMap(areaMaximum));
        return area;
    }

    public void setArea(Map<?, ?> rawMinimum, Map<?, ?> rawMaximum) throws LuaException {
        BlockPos minimum = readPosition(rawMinimum, "minimum");
        BlockPos maximum = readPosition(rawMaximum, "maximum");
        validateArea(minimum, maximum);
        if (minimum.equals(areaMinimum) && maximum.equals(areaMaximum)) {
            return;
        }

        resetSession(true);
        areaMinimum = minimum;
        areaMaximum = maximum;
        setChanged();
        if (!attachedComputers.isEmpty()) {
            beginSessionAndQueueEntries();
        }
    }

    public List<Map<String, Object>> getEntities() {
        if (level == null || level.isClientSide) {
            return List.of();
        }
        if (!sessionActive) {
            beginSession();
        } else {
            scanAndQueueChanges();
        }
        return sortedObservations().stream().map(Observation::luaValue).toList();
    }

    private void beginSession() {
        sessionActive = true;
        observations = captureEntities();
    }

    private void beginSessionAndQueueEntries() {
        beginSession();
        for (Observation observation : sortedObservations()) {
            queueEntered(observation);
        }
    }

    private void scanAndQueueChanges() {
        scanAndQueueChanges(null);
    }

    private void scanAndQueueChanges(IComputerAccess excludedComputer) {
        Map<Entity, Observation> next = captureEntities();

        for (Map.Entry<Entity, Observation> oldEntry : observations.entrySet()) {
            if (!next.containsKey(oldEntry.getKey())) {
                queueLeft(oldEntry.getValue().token(), excludedComputer);
            }
        }
        for (Map.Entry<Entity, Observation> newEntry : next.entrySet()) {
            if (!observations.containsKey(newEntry.getKey())) {
                queueEntered(newEntry.getValue(), excludedComputer);
            }
        }
        observations = next;
    }

    private Map<Entity, Observation> captureEntities() {
        Map<Entity, Observation> result = new IdentityHashMap<>();
        if (level == null || level.isClientSide) {
            return result;
        }

        AABB bounds = detectionBounds();
        List<Entity> entities = level.getEntities((Entity) null, bounds,
                entity -> !(entity instanceof Player player && player.isSpectator()));
        for (Entity entity : entities) {
            Observation previous = observations.get(entity);
            String token = previous == null ? UUID.randomUUID().toString() : previous.token();
            result.put(entity, observe(entity, token));
        }
        return result;
    }

    private Observation observe(Entity entity, String token) {
        Vec3 origin = new Vec3(worldPosition.getX() + 0.5, worldPosition.getY(), worldPosition.getZ() + 0.5);
        Vec3 position = entity.position().subtract(origin);
        Vec3 velocity = entity.getDeltaMovement();
        String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        return new Observation(token, type, category(entity), position, velocity, entity.getBbWidth(), entity.getBbHeight());
    }

    private static String category(Entity entity) {
        if (entity instanceof Player) {
            return "player";
        }
        if (entity instanceof VehicleEntity) {
            return "vehicle";
        }
        if (entity instanceof Projectile) {
            return "projectile";
        }
        if (entity instanceof ItemEntity) {
            return "item";
        }
        if (entity instanceof ExperienceOrb) {
            return "experience";
        }
        if (entity instanceof LivingEntity) {
            return "creature";
        }
        return "other";
    }

    private AABB detectionBounds() {
        return new AABB(
                worldPosition.getX() + areaMinimum.getX(),
                worldPosition.getY() + areaMinimum.getY(),
                worldPosition.getZ() + areaMinimum.getZ(),
                worldPosition.getX() + areaMaximum.getX() + 1,
                worldPosition.getY() + areaMaximum.getY() + 1,
                worldPosition.getZ() + areaMaximum.getZ() + 1);
    }

    private List<Observation> sortedObservations() {
        List<Observation> sorted = new ArrayList<>(observations.values());
        sorted.sort(Comparator.comparing(Observation::token));
        return sorted;
    }

    private void resetSession(boolean report) {
        if (report) {
            computers.forEach(computer -> computer.queueEvent(RESET_EVENT, computer.getAttachmentName()));
        }
        observations = new IdentityHashMap<>();
        sessionActive = false;
    }

    private void queueEntered(Observation observation) {
        queueEntered(observation, null);
    }

    private void queueEntered(Observation observation, IComputerAccess excludedComputer) {
        computers.forEach(computer -> {
            if (computer != excludedComputer) {
                queueEntered(computer, observation);
            }
        });
    }

    private static void queueEntered(IComputerAccess computer, Observation observation) {
        computer.queueEvent(ENTERED_EVENT, computer.getAttachmentName(), observation.luaValue());
    }

    private void queueLeft(String token) {
        queueLeft(token, null);
    }

    private void queueLeft(String token, IComputerAccess excludedComputer) {
        computers.forEach(computer -> {
            if (computer != excludedComputer) {
                computer.queueEvent(LEFT_EVENT, computer.getAttachmentName(), token);
            }
        });
    }

    private void runOnServer(Runnable task) {
        Level currentLevel = level;
        if (currentLevel == null || currentLevel.isClientSide) {
            return;
        }
        MinecraftServer server = currentLevel.getServer();
        if (server == null) {
            return;
        }
        if (server.isSameThread()) {
            task.run();
        } else {
            server.execute(task);
        }
    }

    @Override
    public void setRemoved() {
        attachedComputers.clear();
        resetSession(false);
        super.setRemoved();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        BlockPos minimum = readStoredPosition(tag, AREA_MIN_TAG, DEFAULT_MINIMUM);
        BlockPos maximum = readStoredPosition(tag, AREA_MAX_TAG, DEFAULT_MAXIMUM);
        if (isValidArea(minimum, maximum)) {
            areaMinimum = minimum;
            areaMaximum = maximum;
        } else {
            areaMinimum = DEFAULT_MINIMUM;
            areaMaximum = DEFAULT_MAXIMUM;
        }
        resetSession(false);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putIntArray(AREA_MIN_TAG, new int[]{areaMinimum.getX(), areaMinimum.getY(), areaMinimum.getZ()});
        tag.putIntArray(AREA_MAX_TAG, new int[]{areaMaximum.getX(), areaMaximum.getY(), areaMaximum.getZ()});
    }

    private static BlockPos readStoredPosition(CompoundTag tag, String name, BlockPos fallback) {
        int[] coordinates = tag.getIntArray(name);
        return coordinates.length == 3 ? new BlockPos(coordinates[0], coordinates[1], coordinates[2]) : fallback;
    }

    private static BlockPos readPosition(Map<?, ?> value, String name) throws LuaException {
        return new BlockPos(
                readCoordinate(value, "x", 1, name),
                readCoordinate(value, "y", 2, name),
                readCoordinate(value, "z", 3, name));
    }

    private static int readCoordinate(Map<?, ?> value, String axis, int index, String name) throws LuaException {
        Object coordinate = value.get(axis);
        if (coordinate == null) {
            for (Map.Entry<?, ?> entry : value.entrySet()) {
                if (entry.getKey() instanceof Number number && number.intValue() == index) {
                    coordinate = entry.getValue();
                    break;
                }
            }
        }
        if (!(coordinate instanceof Number number)) {
            throw new LuaException(name + "." + axis + " must be an integer");
        }
        double doubleValue = number.doubleValue();
        if (!Double.isFinite(doubleValue) || doubleValue != Math.rint(doubleValue)
                || doubleValue < Integer.MIN_VALUE || doubleValue > Integer.MAX_VALUE) {
            throw new LuaException(name + "." + axis + " must be an integer");
        }
        return (int) doubleValue;
    }

    private static void validateArea(BlockPos minimum, BlockPos maximum) throws LuaException {
        if (minimum.getX() > maximum.getX() || minimum.getY() > maximum.getY()
                || minimum.getZ() > maximum.getZ()) {
            throw new LuaException("minimum coordinates must not exceed maximum coordinates");
        }
        if (!withinBoundary(minimum) || !withinBoundary(maximum)) {
            throw new LuaException("detection area boundaries must be between -8 and 8");
        }
        if (areaVolume(minimum, maximum) > MAX_VOLUME) {
            throw new LuaException("detection area volume must not exceed 256 blocks");
        }
    }

    private static boolean isValidArea(BlockPos minimum, BlockPos maximum) {
        return minimum.getX() <= maximum.getX()
                && minimum.getY() <= maximum.getY()
                && minimum.getZ() <= maximum.getZ()
                && withinBoundary(minimum)
                && withinBoundary(maximum)
                && areaVolume(minimum, maximum) <= MAX_VOLUME;
    }

    private static boolean withinBoundary(BlockPos position) {
        return withinBoundary(position.getX())
                && withinBoundary(position.getY())
                && withinBoundary(position.getZ());
    }

    private static boolean withinBoundary(int coordinate) {
        return coordinate >= -MAX_BOUNDARY_DISTANCE && coordinate <= MAX_BOUNDARY_DISTANCE;
    }

    private static long areaVolume(BlockPos minimum, BlockPos maximum) {
        return ((long) maximum.getX() - minimum.getX() + 1)
                * ((long) maximum.getY() - minimum.getY() + 1)
                * ((long) maximum.getZ() - minimum.getZ() + 1);
    }

    private static Map<String, Object> coordinateMap(BlockPos position) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("x", position.getX());
        value.put("y", position.getY());
        value.put("z", position.getZ());
        return value;
    }

    private record Observation(String token, String type, String category, Vec3 position, Vec3 velocity,
            float width, float height) {
        private Map<String, Object> luaValue() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("token", token);
            result.put("type", type);
            result.put("category", category);
            result.put("position", vectorMap(position));
            result.put("velocity", vectorMap(velocity));
            Map<String, Object> size = new LinkedHashMap<>();
            size.put("width", width);
            size.put("height", height);
            result.put("size", size);
            return result;
        }

        private static Map<String, Object> vectorMap(Vec3 vector) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("x", vector.x);
            value.put("y", vector.y);
            value.put("z", vector.z);
            return value;
        }
    }
}
