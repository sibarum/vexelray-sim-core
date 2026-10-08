package dev.vexelray.sim.core.gui;

import dev.supirvast.vastir.tools.GpuContext;
import dev.vexelray.gui.core.app.GuiApp;

import java.util.Optional;

/**
 * Compute on the application's own GPU, so that what a simulation's kernels write is a buffer the window's
 * pictures can read where it is, with no copy and no second device.
 *
 * <p>Taken out of vexelray-sim-fluid's 3D session, which did it privately, when the rigid demo needed the same.
 */
public final class AppCompute {

    private AppCompute() {
    }

    /**
     * A context on {@code app}'s device, submitting to the queue the application made for compute: its own, if the
     * application was made with {@link GuiApp.Compute#OWN_QUEUE} and the device has a compute-only family, or else
     * the queue that draws. Empty when that queue cannot compute: a machine where the queue family that presents is
     * not one that computes, and compute has no queue of its own. A simulation then runs on a device of its own, and
     * a picture of it has to be copied across.
     *
     * <p>On a queue of its own ({@link GuiApp.Gpu#ownQueue()}), nothing orders the context's work against the
     * frame's but timeline semaphores: a picture of a buffer has to wait for the step that writes it, and the step
     * for the frames still reading it.
     *
     * <p>The context is the caller's to close, before the application closes its device. Closing it leaves the
     * device open.
     */
    public static Optional<GpuContext> lend(GuiApp app) {
        GuiApp.Gpu gpu = app.gpu();
        try {
            return Optional.of(GpuContext.on(gpu.instance(), gpu.device(), gpu.computeFamily()));
        } catch (IllegalArgumentException cannot) {
            return Optional.empty();
        }
    }
}
