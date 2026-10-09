package dev.vexelray.sim.core.gui;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ring's three roles, with no device: the writer never keeps a step in the slot the frame reads or the one it has
 * been offered, the frame always takes the newest finished step, and a generation is retired only once the frame has
 * moved past it.
 */
class ShownRingTest {

    private static ShownRing.Generation<String> generation(long id) {
        return new ShownRing.Generation<>(id, new long[] {10 * id, 10 * id + 1, 10 * id + 2}, 99, "box " + id);
    }

    @Test
    void nothingIsTakenBeforeAStepIsPublished() {
        ShownRing<String> ring = new ShownRing<>();
        ring.install(generation(1));
        assertNull(ring.take());
    }

    @Test
    void theWriterNeverKeepsAStepWhereTheFrameReadsOrHasBeenOffered() {
        ShownRing<String> ring = new ShownRing<>();
        ring.install(generation(1));
        ShownRing.Frame<String> front = null;
        for (long step = 1; step <= 200; step++) {
            int back = ring.back();
            if (front != null) {
                assertNotEquals(front.slot(), back, "step " + step + " would be kept in the slot the frame reads");
            }
            ring.publish(step, step * 1_000_000L);
            assertNotEquals(back, ring.back(), "nor in the slot just offered");
            if (step % 3 == 0 || step % 7 == 0) {   // frames now and then, at no fixed rate
                front = ring.take();
                assertEquals(step, front.step(), "the frame takes the newest finished step");
            }
        }
    }

    @Test
    void aFrameWithNothingNewKeepsWhatItHas() {
        ShownRing<String> ring = new ShownRing<>();
        ring.install(generation(1));
        ring.publish(1, 1);
        ShownRing.Frame<String> first = ring.take();
        assertSame(first, ring.take());
        assertEquals("box 1", first.generation().meta());
    }

    @Test
    void anOldGenerationIsRetiredOnlyOnceTheFrameHasMovedPastIt() {
        ShownRing<String> ring = new ShownRing<>();
        ShownRing.Generation<String> one = generation(1);
        ShownRing.Generation<String> two = generation(2);
        ring.install(one);
        ring.publish(1, 1);
        ring.take();
        ring.install(two);
        ring.publish(1, 2);
        assertEquals(List.of(), ring.retired(), "the frame still reads the first generation's slot");
        assertSame(two, ring.take().generation());
        assertEquals(List.of(one), ring.retired());
        assertEquals(List.of(), ring.retired(), "each once");
    }

    @Test
    void aGenerationTheFrameNeverTookIsRetiredAtTheNext() {
        ShownRing<String> ring = new ShownRing<>();
        ShownRing.Generation<String> one = generation(1);
        ring.install(one);
        ring.publish(1, 1);
        ring.take();
        ShownRing.Generation<String> two = generation(2);
        ring.install(two);
        ring.publish(1, 2);
        ring.install(generation(3));
        assertEquals(List.of(two), ring.retired(), "never read, so free at once");
        Set<Long> live = new HashSet<>();
        ring.all().forEach(g -> live.add(g.id()));
        assertTrue(live.containsAll(Set.of(1L, 3L)), "the front's and the current one are still live: " + live);
    }

    @Test
    void aSimulationGoneWithNoneAfterItIsRetiredOnceTheFrameLetsGo() {
        ShownRing<String> ring = new ShownRing<>();
        ShownRing.Generation<String> one = generation(1);
        ring.install(one);
        ring.publish(1, 1);
        ring.take();
        ring.install(null);
        assertEquals(List.of(), ring.retired(), "the frame may still be drawing its front");
        ring.release();
        assertEquals(List.of(one), ring.retired());
        assertNull(ring.take(), "nothing to draw until another simulation keeps a step");
    }
}
