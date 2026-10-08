package dev.vexelray.sim.core.gui;

import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.Node;
import dev.vexelray.gui.core.input.DragEvent;
import dev.vexelray.gui.core.input.InputTopics;
import dev.vexelray.gui.core.layout.NodeLayout;
import dev.vexelray.gui.core.layout.Rect;
import sibarum.atchung.Subscription;
import sibarum.tactroller.api.InputEvent;

/**
 * An {@link Orbit} turned by dragging in a node and zoomed by the wheel over it.
 *
 * <p>The pointer is held for a drag and warped back, because turning is a displacement and the pointer would
 * otherwise leave the node a quarter of the way round. The wheel comes from the one device bus, like every other
 * input: scroll dispatch belongs to scrollable containers, and a picture is not one, so the event is read where it is
 * published and answered only when the pointer is over the node and the control is {@link #active}. A touchpad's
 * two-finger scroll arrives as the same event in small fractions of a notch, which {@link Orbit#zoom} takes as they
 * come.
 *
 * <p>Taken out of vexelray-sim-fluid's 3D view when the rigid demo needed the same camera.
 */
public final class OrbitControl implements AutoCloseable {

    /** A drag across the whole node turns the camera this far, in radians: half a turn across, a quarter down. */
    public static final double DRAG_YAW = Math.PI;
    public static final double DRAG_PITCH = Math.PI / 2;

    private final Orbit orbit;
    private final Node node;
    private final Subscription wheel;

    /** Whether the node is showing what this camera looks at: the wheel over it is this control's only then. */
    private volatile boolean active = true;

    /** Wires {@code orbit} to drags in {@code node} and the wheel over it. */
    public OrbitControl(Gui gui, Node node, Orbit orbit) {
        this.orbit = orbit;
        this.node = node;
        gui.onDrag(node, this::drag);
        gui.dragLocksPointer(node, true);
        wheel = gui.bus().subscribe(InputTopics.INPUT, event -> {
            if (event instanceof InputEvent.Scrolled s && s.yOffset() != 0 && active && over(s.x(), s.y())) {
                orbit.zoom(s.yOffset());
                gui.wakeForInput();               // a paused simulation owes no frame, and the picture has moved
            }
        });
    }

    /** The camera this moves. */
    public Orbit orbit() {
        return orbit;
    }

    /** Whether the wheel over the node is this control's; a node showing something else sets it false. */
    public void active(boolean active) {
        this.active = active;
    }

    private void drag(DragEvent e) {
        if (e.phase() != DragEvent.Phase.MOVE) {
            return;                                   // letting go is not a movement
        }
        orbit.turn(e.dx() / Math.max(1f, e.nodeW()) * DRAG_YAW, e.dy() / Math.max(1f, e.nodeH()) * DRAG_PITCH);
    }

    /**
     * Whether a point, in the coordinates the input events and the layout share, is on the part of the node that can
     * be seen and pointed at, which is what a wheel turned over it is aimed at.
     */
    private boolean over(int x, int y) {
        NodeLayout layout = node.layout();
        if (!layout.present()) {
            return false;
        }
        Rect box = layout.visibleRect();
        return x >= box.x() && x < box.x() + box.w() && y >= box.y() && y < box.y() + box.h();
    }

    /** Stops answering the wheel. The drag handler goes with the node. */
    @Override
    public void close() {
        active = false;
        wheel.close();
    }
}
