# vexelray-sim-core

What the VexelRay simulations share: **infrastructure, not technique**. Every simulation pays for this part once.
A technique, such as a scatter, a solver or a contact model, stays in the simulation that chose it until a second one
asks for the same thing.

It is drawn out of [vexelray-sim-fluid](../vexelray-sim-fluid). [vexelray-sim-rigid](../vexelray-sim-rigid) builds on it
too, and [vexelray-sim-physics](../vexelray-sim-physics), which couples the two and is the front door for an
application. A contract the fluid and the bodies must both meet, such as what a solid is to the fluid, belongs here,
so that neither imports the other.

| Module | What it holds |
| --- | --- |
| `vexelray-sim-core` | `time.Clock`: the host's integer clock, which grants the kernel a budget of microsecond ticks and settles what it spent. The JDK only. |
| `vexelray-sim-core-gui` | `BudgetController`: sizes a tick's work from measured throughput. `Orbit`: the camera that circles a box centred on the origin. `Builds`: slow work on the application's offload lane, only the latest kept. `Dial` and `Pick`: control-panel rows for a number on a slider and a choice behind a menu. `OrbitControl`: an `Orbit` turned by dragging in a node and zoomed by the wheel over it. `AppCompute`: a compute context on the window's own GPU, so a picture reads the simulation's buffers where they are. `DemoLook`: the demos' theme and type. |

Some of what started here turned out not to be a simulation's at all, and went further down:

- **To SupirVast:** `Body`, which writes kernels, and `Pass`, `BufferSpec` and `Buffered`, a step as data, are
  in vastir (`dev.supirvast.vastir.build`, `dev.supirvast.vastir.pass`). `PassRunner` in vastir-tools runs such a
  step on the GPU or the CPU.
- **To Kronometer:** time, rates and curves. The demos' timing is Kronometer's, not anything here.

## Running

The stack's siblings must be installed to the local Maven repository first: `supirvast`, `vexelray`,
`tactroller`, `vexelray-gui`.

```bash
mvn install
```

Install this before building a simulation that depends on it.
