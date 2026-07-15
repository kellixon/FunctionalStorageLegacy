package com.xinyihl.functionalstoragelegacy;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import java.lang.reflect.Constructor;
import java.util.concurrent.Callable;

/** Minimal capability injection fixture for plain JUnit workers. */
public final class TestCapabilities {

    private TestCapabilities() {
    }

    public static synchronized Capability<IItemHandler> itemHandler() {
        if (CapabilityItemHandler.ITEM_HANDLER_CAPABILITY != null) {
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY;
        }
        try {
            Constructor<Capability> constructor = Capability.class.getDeclaredConstructor(
                    String.class, Capability.IStorage.class, Callable.class);
            constructor.setAccessible(true);
            @SuppressWarnings("unchecked")
            Capability<IItemHandler> capability = (Capability<IItemHandler>) constructor.newInstance(
                    "functionalstoragelegacy_test:item_handler", null, null);
            CapabilityItemHandler.ITEM_HANDLER_CAPABILITY = capability;
            return capability;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to initialize item handler capability", exception);
        }
    }
}
