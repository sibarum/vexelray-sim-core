package dev.vexelray.sim.core.gui;

import dev.supirvast.vastir.build.Body;
import dev.supirvast.vastir.tools.Accelerator;
import dev.supirvast.vastir.tools.ResidentBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A step kept beside its simulation: the slot holds the buffer as it was, the timeline reaches the step's value with
 * it, and writing the buffer again leaves a kept slot alone.
 */
class KeptSlotsTest {

    @Test
    void aKeptStepIsTheBufferAsItWasAndSignalsItsValue() {
        try (Accelerator accelerator = new Accelerator()) {
            assumeTrue(accelerator.capabilities().gpuAvailable(), "no Vulkan device");
            int words = 1000;
            try (ResidentBuffer source = accelerator.allocate(Body.F32, words);
                 KeptSlots slots = new KeptSlots(accelerator, source)) {
                int[] first = new int[words];
                int[] second = new int[words];
                for (int k = 0; k < words; k++) {
                    first[k] = Float.floatToRawIntBits(k * 0.5f);
                    second[k] = Float.floatToRawIntBits(-k);
                }
                source.write(first);
                slots.keep(0, 1).await();
                assertEquals(1, slots.reached());
                source.write(second);
                slots.keep(1, 2).await();
                assertEquals(2, slots.reached());
                assertArrayEquals(first, slots.read(0), "a kept slot is not touched by the next step");
                assertArrayEquals(second, slots.read(1));
                assertEquals(3, slots.generation(1, "meta").slots().length);
            }
        }
    }

    @Test
    void aBufferOnTheHostHasNothingToKeepBeside() {
        try (Accelerator accelerator = new Accelerator()) {
            assumeTrue(!accelerator.capabilities().gpuAvailable(), "only where there is no GPU, so buffers are the host's");
            try (ResidentBuffer source = accelerator.allocate(Body.F32, 4)) {
                assertThrows(IllegalArgumentException.class, () -> new KeptSlots(accelerator, source));
            }
        }
    }
}
