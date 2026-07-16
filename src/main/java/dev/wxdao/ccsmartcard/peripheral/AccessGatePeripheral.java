package dev.wxdao.ccsmartcard.peripheral;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dev.wxdao.ccsmartcard.block.entity.GateCabinetBlockEntity;

public final class AccessGatePeripheral implements IPeripheral {
    private final GateCabinetBlockEntity cabinet;

    public AccessGatePeripheral(GateCabinetBlockEntity cabinet) {
        this.cabinet = cabinet;
    }

    @Override
    public String getType() {
        return "access_gate";
    }

    @Override
    public void attach(IComputerAccess computer) {
        cabinet.attach(computer);
    }

    @Override
    public void detach(IComputerAccess computer) {
        cabinet.detach(computer);
    }

    @Override
    public Object getTarget() {
        return cabinet;
    }

    @LuaFunction(mainThread = true)
    public final Object getGateId() {
        return cabinet.getGateId();
    }

    @LuaFunction(mainThread = true)
    public final String getState() {
        return cabinet.getGateState();
    }

    @LuaFunction(mainThread = true)
    public final int getWidth() {
        return cabinet.getGateWidth();
    }

    @LuaFunction(mainThread = true)
    public final MethodResult open() {
        return command(true);
    }

    @LuaFunction(mainThread = true)
    public final MethodResult close() {
        return command(false);
    }

    private MethodResult command(boolean open) {
        return cabinet.setTargetOpen(open)
                ? MethodResult.of(true)
                : MethodResult.of(null, "unpaired");
    }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof AccessGatePeripheral peripheral && peripheral.cabinet == cabinet;
    }
}
