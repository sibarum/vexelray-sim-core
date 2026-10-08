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
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.app.GuiApp;
import dev.vexelray.gui.harness.HarnessApp;
import dev.vexelray.os.WindowConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static dev.supirvast.vastir.build.Body.I32;
import static dev.supirvast.vastir.build.Body.i;
import static dev.supirvast.vastir.build.Body.lt;
import static dev.supirvast.vastir.build.Body.mul;
import static dev.supirvast.vastir.build.Body.v;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AppCompute#lend}: a context on the queue the application made for compute, whose kernels run there and
 * write buffers on the device the application draws on.
 *
 * <p>Everything Vulkan is done on the loop's own thread, through {@code post}, as an application's would be.
 * Needs a Vulkan device; this is an integration test, not a unit test.
 */
class AppComputeTest {

    private static final int N = 4096;

    @Test
    void sharingLendsTheQueueThatDraws() throws Exception {
        try (HarnessApp harness = HarnessApp.start(new Gui(), WindowConfig.of("shared", 200, 120))) {
            int[] families = onLoop(harness, () -> {
                GuiApp.Gpu gpu = harness.app().gpu();
                try (GpuContext context = AppCompute.lend(harness.app()).orElseThrow()) {
                    return new int[] {gpu.device().queueFamilyIndex(), context.queueFamily()};
                }
            });
            assertEquals(families[0], families[1]);
        }
    }

    @Test
    void aQueueOfItsOwnIsLentAndRunsKernels() throws Exception {
        try (HarnessApp harness = HarnessApp.start(new Gui(), WindowConfig.of("own queue", 200, 120),
                GuiApp.Compute.OWN_QUEUE)) {
            int[] seen = onLoop(harness, () -> {
                GuiApp.Gpu gpu = harness.app().gpu();
                try (GpuContext context = AppCompute.lend(harness.app()).orElseThrow();
                     Accelerator accelerator = Accelerator.on(context)) {
                    assertEquals(gpu.computeFamily(), context.queueFamily(), "lent the queue made for compute");
                    System.out.println("[app compute] " + context.deviceName() + ": draws on family "
                            + gpu.device().queueFamilyIndex() + ", lent family " + context.queueFamily());
                    ResidentBuffer data = accelerator.allocate(I32, N);
                    KernelHandle triple = accelerator.register(new KernelSpec(triple(),
                            List.of(KernelColumn.output("data", 0, I32).withLength(N))).withWorkgroupSize(64))
                            .orElseThrow();
                    try (DispatchSequence sequence = accelerator.sequence()
                            .dispatch(triple, List.of(data), N).build()) {
                        assertTrue(sequence.recorded(), "the sequence fell back to the CPU");
                        Completion done = sequence.run();
                        done.await();
                        return data.read();
                    }
                }
            });
            int[] expected = new int[N];
            for (int k = 0; k < N; k++) {
                expected[k] = 3 * k;
            }
            assertArrayEquals(expected, seen);
        }
    }

    /** {@code data[k] = 3k}. */
    private static Function triple() {
        Buffer data = new Buffer("data", 0, I32);
        Body b = new Body();
        LocalVar k = b.let("k", new Expr.InvocationId());
        b.when(lt(v(k), new Expr.InvocationCount()), t -> t.store(data, v(k), mul(v(k), i(3))));
        return new Function("kernel", new Type.FunctionType(Type.VOID, List.of()), b.finish());
    }

    /** Run {@code work} on the loop thread and hand back what it returned. */
    private static <T> T onLoop(HarnessApp harness, Supplier<T> work) throws Exception {
        CompletableFuture<T> done = new CompletableFuture<>();
        harness.app().post(() -> {
            try {
                done.complete(work.get());
            } catch (RuntimeException | Error e) {
                done.completeExceptionally(e);
            }
        });
        return done.get(30, TimeUnit.SECONDS);
    }
}
