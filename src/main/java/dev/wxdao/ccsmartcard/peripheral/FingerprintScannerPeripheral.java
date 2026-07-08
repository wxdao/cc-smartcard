package dev.wxdao.ccsmartcard.peripheral;

import dev.wxdao.ccsmartcard.block.entity.FingerprintScannerBlockEntity;
import dan200.computercraft.api.lua.ILuaCallback;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class FingerprintScannerPeripheral implements IPeripheral {
    private static final String SCAN_COMPLETE_EVENT = "fingerprint_scanner_scan_complete";
    private static final AtomicLong NEXT_INSTANCE_ID = new AtomicLong();

    private final FingerprintScannerBlockEntity scanner;
    private final long instanceId = NEXT_INSTANCE_ID.incrementAndGet();
    private long scanSequence;
    private long cancelSequence;

    public FingerprintScannerPeripheral(FingerprintScannerBlockEntity scanner) {
        this.scanner = scanner;
    }

    @Override
    public String getType() {
        return "fingerprint_scanner";
    }

    @Override
    public void attach(IComputerAccess computer) {
        scanner.attach(computer);
    }

    @Override
    public void detach(IComputerAccess computer) {
        scanner.detach(computer);
    }

    @Override
    public Object getTarget() {
        return scanner;
    }

    @LuaFunction
    public final MethodResult scan(IComputerAccess computer) {
        long baselineScanSequence = scanSequence;
        long baselineCancelSequence = cancelSequence;
        return waitForScan(baselineScanSequence, baselineCancelSequence);
    }

    public void completeScan(Player player) {
        long sequence = ++scanSequence;
        scanner.queueEvent(SCAN_COMPLETE_EVENT,
                instanceId,
                sequence,
                cancelSequence,
                true,
                player.getUUID().toString(),
                player.getGameProfile().getName());
    }

    public void cancelPendingScans() {
        long sequence = ++cancelSequence;
        scanner.queueEvent(SCAN_COMPLETE_EVENT, instanceId, scanSequence, sequence, false, "scan cancelled");
    }

    public void cancelPendingScans(IComputerAccess computer) {
        long sequence = ++cancelSequence;
        scanner.queueEvent(computer, SCAN_COMPLETE_EVENT, instanceId, scanSequence, sequence, false, "scan cancelled");
    }

    private MethodResult waitForScan(long baselineScanSequence, long baselineCancelSequence) {
        ILuaCallback callback = event -> resumeScan(baselineScanSequence, baselineCancelSequence, event);
        return MethodResult.pullEvent(SCAN_COMPLETE_EVENT, callback);
    }

    private MethodResult resumeScan(long baselineScanSequence, long baselineCancelSequence, Object[] event) {
        int offset = event.length >= 1 && SCAN_COMPLETE_EVENT.equals(event[0]) ? 1 : 0;
        if (event.length < offset + 5 || !Long.valueOf(instanceId).equals(asLong(event[offset]))) {
            return waitForScan(baselineScanSequence, baselineCancelSequence);
        }

        long eventScanSequence = valueOrZero(asLong(event[offset + 1]));
        long eventCancelSequence = valueOrZero(asLong(event[offset + 2]));
        boolean success = Boolean.TRUE.equals(event[offset + 3]);
        if (success && eventScanSequence > baselineScanSequence) {
            if (event.length < offset + 6) {
                return waitForScan(baselineScanSequence, baselineCancelSequence);
            }
            return MethodResult.of(Map.of(
                    "uuid", event[offset + 4],
                    "name", event[offset + 5]));
        }
        if (!success && eventCancelSequence > baselineCancelSequence) {
            return MethodResult.of(null, String.valueOf(event[offset + 4]));
        }
        return waitForScan(baselineScanSequence, baselineCancelSequence);
    }

    private static long valueOrZero(Long value) {
        return value == null ? 0 : value;
    }

    private static Long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof FingerprintScannerPeripheral peripheral && peripheral.scanner == scanner;
    }
}
