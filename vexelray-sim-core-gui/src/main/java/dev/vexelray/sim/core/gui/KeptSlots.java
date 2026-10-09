package dev.vexelray.sim.core.gui;

import dev.supirvast.vastir.build.Body;
import dev.supirvast.vastir.core.Buffer;
import dev.supirvast.vastir.core.Expr;
import dev.supirvast.vastir.core.Function;
import dev.supirvast.vastir.core.LocalVar;
import dev.supirvast.vastir.tools.Accelerator;
import dev.supirvast.vastir.tools.Completion;
import dev.supirvast.vastir.tools.DispatchSequence;
import dev.supirvast.vastir.tools.GpuContext;
import dev.supirvast.vastir.tools.KernelColumn;
import dev.supirvast.vastir.tools.KernelHandle;
import dev.supirvast.vastir.tools.KernelSpec;
import dev.supirvast.vastir.tools.ResidentBuffer;
import dev.supirvast.vastir.type.Type;

import java.util.List;

import static dev.supirvast.vastir.build.Body.F32;
import static dev.supirvast.vastir.build.Body.load;
import static dev.supirvast.vastir.build.Body.lt;
import static dev.supirvast.vastir.build.Body.v;

/**
 * A {@link ShownRing}'s buffers, beside the simulation whose steps they keep: {@link ShownRing#SLOTS} copies of one of
 * its resident buffers, on the device it computes on, and the timeline a kept step signals.
 *
 * <p>A step is kept by a copy on the GPU, from the simulation's buffer to a slot, and the copy sets the timeline to the
 * step's value once it is there. A picture on the same device then reads the slot while the simulation writes its own
 * buffer again, and waits for that value inside the GPU, which the step has already reached by the time it is
 * published. Nothing crosses the bus.
 *
 * <p>The stepping thread's, as the simulation is: made, kept into and closed there.
 */
public final class KeptSlots implements AutoCloseable {

    private static final int WORKGROUP = 64;
    private static final List<Buffer> BUFFERS = List.of(new Buffer("source", 0, F32), new Buffer("slot", 1, F32));

    private final Accelerator accelerator;
    private final ResidentBuffer[] slots = new ResidentBuffer[ShownRing.SLOTS];
    private final DispatchSequence[] keeps = new DispatchSequence[ShownRing.SLOTS];
    private final KernelHandle keeper;
    private final GpuContext.Timeline timeline;

    /**
     * Slots for {@code source}, a buffer of 32-bit words resident on {@code accelerator}, which must be on a GPU: a
     * buffer on the host has no device to keep it beside. The accelerator is used, and not closed.
     */
    public KeptSlots(Accelerator accelerator, ResidentBuffer source) {
        if (!source.onDevice()) {
            throw new IllegalArgumentException("a buffer on the host has no device to keep its steps beside");
        }
        this.accelerator = accelerator;
        int words = source.elements();
        this.keeper = accelerator.register(new KernelSpec(keep(), List.of(
                KernelColumn.output("source", 0, F32).withLength(words),
                KernelColumn.output("slot", 1, F32).withLength(words)))
                .withWorkgroupSize(WORKGROUP)).orElseThrow();
        this.timeline = accelerator.timeline(0);
        for (int k = 0; k < slots.length; k++) {
            slots[k] = accelerator.allocate(F32, words);
            keeps[k] = accelerator.sequence().dispatch(keeper, List.of(source, slots[k]), words).build();
        }
    }

    /**
     * Keeps the simulation's buffer as it stands after everything submitted so far in slot {@code slot}, and sets the
     * timeline to {@code value} once it is there, on the GPU.
     *
     * @return the copy's completion: the step is the picture's to take once it is done
     */
    public Completion keep(int slot, long value) {
        return keeps[slot].run(List.of(), List.of(timeline.at(value)));
    }

    /**
     * The slots as a picture binds them.
     *
     * @param id   the generation's number, which the ring counts
     * @param meta what a picture needs besides the slots
     */
    public <M> ShownRing.Generation<M> generation(long id, M meta) {
        long[] handles = new long[slots.length];
        for (int k = 0; k < slots.length; k++) {
            handles[k] = slots[k].vkBuffer();
        }
        return new ShownRing.Generation<>(id, handles, timeline.handle(), meta);
    }

    /** What slot {@code k} holds, read back: for a test. Waits. */
    int[] read(int k) {
        return slots[k].read();
    }

    /** The timeline's value now: for a test. */
    long reached() {
        return timeline.value();
    }

    /** Waits for what is in flight, then frees the slots and the timeline. Before the simulation they are beside. */
    @Override
    public void close() {
        accelerator.finish();
        for (DispatchSequence keep : keeps) {
            keep.close();
        }
        for (ResidentBuffer slot : slots) {
            slot.close();
        }
        accelerator.release(keeper);
        timeline.close();
    }

    /** One invocation per word: the whole of {@code source} copied into {@code slot}. */
    static Function keep() {
        Body b = new Body();
        LocalVar k = b.let("k", new Expr.InvocationId());
        b.when(lt(v(k), new Expr.InvocationCount()),
                t -> t.store(BUFFERS.get(1), v(k), load(BUFFERS.get(0), v(k))));
        return new Function("keptSlotsKeep", new Type.FunctionType(Type.VOID, List.of()), b.finish());
    }
}
