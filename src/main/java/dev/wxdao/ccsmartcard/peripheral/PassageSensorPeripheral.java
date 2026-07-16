package dev.wxdao.ccsmartcard.peripheral;

import dev.wxdao.ccsmartcard.block.entity.PassageSensorBlockEntity;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;

import java.util.List;
import java.util.Map;

public final class PassageSensorPeripheral implements IPeripheral {
    private final PassageSensorBlockEntity sensor;

    public PassageSensorPeripheral(PassageSensorBlockEntity sensor) {
        this.sensor = sensor;
    }

    @Override
    public String getType() {
        return "passage_sensor";
    }

    @Override
    public void attach(IComputerAccess computer) {
        sensor.attach(computer);
    }

    @Override
    public void detach(IComputerAccess computer) {
        sensor.detach(computer);
    }

    @Override
    public Object getTarget() {
        return sensor;
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> getArea() {
        return sensor.getArea();
    }

    @LuaFunction(mainThread = true)
    public final boolean setArea(Map<?, ?> minimum, Map<?, ?> maximum) throws LuaException {
        sensor.setArea(minimum, maximum);
        return true;
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getEntities() {
        return sensor.getEntities();
    }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof PassageSensorPeripheral peripheral && peripheral.sensor == sensor;
    }
}
