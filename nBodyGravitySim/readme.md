# N-Body Gravitational Simulation

A real-time gravitational simulation of the solar system built in Java using OpenGL for rendering. Simulates gravitational interactions between hundreds of bodies including planets and an asteroid belt, optimized using the Barnes-Hut algorithm for near-linear performance scaling. 

![Simulation while running on a machine] (gravity_sim.jpg)

---

## Features

- Real-time N-body gravitational simulation with accurate Newtonian physics
- Barnes-Hut quadtree optimization reducing force calculations from O(n²) to O(n log n)
- Leapfrog integration for long-term orbital stability and energy conservation
- Full solar system with 8 planets and a 500-body asteroid belt
- OpenGL rendering via LWJGL with pan and zoom camera controls
- Debug overlays for velocity vectors, acceleration vectors, and quadtree visualization

---

## Planned Features

- Creating bodies while the simulation is running.
- Tweaking time scale during run time. (Completed)
- Texturing of bodies dynamically
- GUI (Completed)

---

## Physics

### Newtonian Gravity

Every body in the simulation exerts a gravitational force on every other body according to Newton's law of universal gravitation:

```
F = G × m₁ × m₂ / r²
```

Where `G` is the gravitational constant (6.674 × 10⁻¹¹ N⋅m²/kg²), `m₁` and `m₂` are the masses of the two bodies, and `r` is the distance between them. From this force, each body's acceleration is derived via Newton's second law (`F = ma`), which is then used to update velocity and position each timestep.

### Softening

When two bodies pass very close together, `r` approaches zero and the force approaches infinity, which would destabilize the simulation. A softening factor `ε` is added to the denominator to prevent this:

```
F = G × m₁ × m₂ / (r² + ε²)
```

This small fudge factor keeps forces finite during close encounters without meaningfully affecting bodies at normal distances.

### Leapfrog Integration

A naive Euler integrator applies acceleration, velocity, and position updates all in one shot each frame. This causes orbits to slowly gain or lose energy over time.  The result would be planets spiraling inward or outward rather than maintaining stable orbits.

Leapfrog integration solves this by splitting the velocity update into two half-steps that straddle the position update:

```
½ velocity kick  →  position drift  →  recalculate forces  →  ½ velocity kick
```

This scheme conserves energy over long periods, keeping orbits stable across thousands of simulated years. Euler integration produces visibly drifting orbits within minutes while Leapfrog maintains stable circular orbits indefinitely.

### Barnes-Hut Algorithm

The simple approach to N-body simulation checks every body against every other body to calculate forces — O(n²) complexity. With 500 bodies that's 250,000 force calculations per frame, and with 5,000 bodies it becomes 25,000,000. This quickly scales to the point where your computer will not be happy.

The Barnes-Hut algorithm reduces this to O(n log n) using a quadtree data structure:

**Building the tree**: Space is recursively subdivided into four equal quadrants. Each node stores the total mass and center of mass of all bodies within its region. The tree is rebuilt from scratch every frame as bodies move.

**The θ (theta) approximation**: When calculating the force on a body, the algorithm walks the tree and at each internal node asks: is this cluster of bodies far enough away to treat as a single mass? The condition is:

```
s / d < θ
```

Where `s` is the width of the node's region and `d` is the distance from the body to the node's center of mass. If true, the entire cluster is approximated as one body at its center of mass. If false, the algorithm recurses into the node's children.

A `θ` of 0.5 gives a good balance of accuracy and performance. Increasing it toward 1.0 or higher speeds up the simulation at the cost of accuracy. Setting it to 0 degrades to brute force O(n²).

**Result**: At θ = 0.5 with 509 bodies (8 planets + 500 asteroids), Barnes-Hut reduces force calculations from ~259,000 to roughly 5,000–10,000 per frame, enabling smooth real-time simulation.

### Momentum Conservation

The star is not fixed in place — it responds to the gravitational pull of all planets, most significantly Jupiter. Without correction the entire system would drift off screen over time.

At initialization the simulation calculates the total momentum of all planets and assigns the star an equal and opposite momentum, ensuring the system's center of mass remains stationary. This produces the subtle stellar wobble seen in the simulation. This is the same effect astronomers use to detect real exoplanets.

---

## Project Structure

```
src/
└── com/gravitysim/
    ├── Main.java                    — Entry point, initial conditions
    ├── core/
    │   ├── Vector2D.java            — 2D math utility
    │   ├── Body.java                — Particle state and Leapfrog integration
    │   └── Simulation.java          — Physics loop and timestep management
    ├── tree/
    │   ├── BoundingBox.java         — Spatial region representation
    │   ├── QuadTreeNode.java        — Recursive tree node, force calculation
    │   └── QuadTree.java            — Tree wrapper, boundary computation
    └── renderer/
        ├── Camera.java              — Pan and zoom view matrix
        └── SimulationRenderer.java  — LWJGL/OpenGL rendering pipeline
```

---

## Dependencies

| Library | Version | Purpose |
|---|---|---|
| LWJGL | 3.3.4 | OpenGL and GLFW bindings |
| JOML | 1.10.5 | Matrix and vector math for OpenGL |
| ImGui-java | 1.86.11 | Debug UI overlay (planned) |

---

## Requirements

- Java 21 (LTS)
- Maven 3.6+
- A GPU supporting OpenGL 3.3 or higher
- Linux: X11 display server (Wayland requires the X11 backend hint — already configured)

---

## How to Run

**1. Clone the repository**

```bash
git clone https://github.com/yourusername/nBodyGravitySim.git
cd nBodyGravitySim
```

**2. Build with Maven**

```bash
mvn clean compile
```

**3. Run**

```bash
mvn exec:java -Dexec.mainClass="com.gravitysim.Main" \
              -Dexec.jvmArgs="--enable-native-access=ALL-UNNAMED"
```

Or from Eclipse: right-click `Main.java` → Run As → Java Application. Ensure `--enable-native-access=ALL-UNNAMED` is set under Run Configurations → VM Arguments.

---

## Controls

| Input | Action |  
|---|---|  
| Scroll wheel | Zoom in / out |  
| Left-click drag | Pan camera |  
| `V` | Toggle velocity vectors |  
| `A` | Toggle acceleration vectors |  
| `Q` | Toggle quadtree grid overlay |  
| `Escape` | Exit |  

---

## Configuration

Key simulation parameters can be tuned in `Main.java` and `Simulation.java`:

| Parameter | Location | Default | Effect |
|---|---|---|---|
| `G` | `Simulation` | 6.674e-11 | Gravitational constant |
| `epsilon` | `Simulation` | 1e8 | Softening factor — increase to stabilize close encounters |
| `dt` | `Simulation` | 3600 | Timestep in seconds (1 hour per step) |
| `theta` | `QuadTree` | 0.5 | Barnes-Hut accuracy threshold — increase for performance |
| `asteroidCount` | `Main` | 500 | Number of asteroid belt bodies |

---

## Performance

Tested with 2009 bodies (9 planets + 2000 asteroids) on an NVIDIA GPU with OpenGL 3.3:

| Bodies | Algorithm | Force calculations/frame | Performance |
|---|---|---|---|
| 2009 | Brute force O(n²) | 4,036,081 | Slow |
| 2009| Barnes-Hut O(n log n) | ~5,000–10,000 | Smooth real-time |

Depending on the 'theta' setting, the number of bodies can be increase or decreased to hit a desired framerate.

