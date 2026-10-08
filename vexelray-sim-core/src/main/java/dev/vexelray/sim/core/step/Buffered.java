package dev.vexelray.sim.core.step;

import java.util.Map;

/**
 * A step that names its buffers: what a runner allocates before it runs the step's {@link Pass passes} over them.
 *
 * <p>A step is data — which kernel, over which named buffers, with how many invocations, in order — and nothing that
 * describes one runs anything, so a runner on any backend can take the same description.
 */
public interface Buffered {

    /** Every buffer the passes name, in a stable order. */
    Map<String, BufferSpec> buffers();
}
