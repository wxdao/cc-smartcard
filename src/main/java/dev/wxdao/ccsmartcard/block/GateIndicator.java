package dev.wxdao.ccsmartcard.block;

import net.minecraft.util.StringRepresentable;

public enum GateIndicator implements StringRepresentable {
    RED("red"),
    AMBER("amber"),
    GREEN("green");

    private final String serializedName;

    GateIndicator(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
