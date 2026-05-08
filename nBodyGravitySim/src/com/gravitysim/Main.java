package com.gravitysim;

import java.util.ArrayList;
import java.util.List;

import com.gravitysim.core.Body;
import com.gravitysim.core.Simulation;
import com.gravitysim.core.Vector2D;
import com.gravitysim.renderer.SimulationRenderer;

public class Main {

	public static void main(String[] args) {
		System.setProperty("org.lwjgl.glfw.Display", ":0");
		try {
		Simulation sim = new Simulation(6.674e-11, 1e8, 3600);
		
		double G = 6.674e-11;
		double M = 1.989e30; // star mass

		// Planet orbital parameters — {radius in AU, mass in kg, start angle in degrees}
		double[][] planets = {
		    { 0.4  * 1.496e11, 3.3e23,  0   },  // Mercury-like
		    { 0.7  * 1.496e11, 4.87e24, 90  },  // Venus-like
		    { 1.0  * 1.496e11, 5.97e24, 180 },  // Earth-like
		    { 1.5  * 1.496e11, 6.39e23, 270 },  // Mars-like
		    { 3.5 * 1.496e11, 1.898e27, 45 },  // Jupiter-like
		};

		// Calculate total momentum of all planets first
		double totalMomentumX = 0;
		double totalMomentumY = 0;

		List<Body> planetBodies = new ArrayList<>();
		for (double[] p : planets) {
		    double r     = p[0];
		    double mass  = p[1];
		    double angle = Math.toRadians(p[2]);

		    // Position around the star
		    double px = r * Math.cos(angle);
		    double py = r * Math.sin(angle);

		    // Circular orbit velocity — perpendicular to radius
		    double speed = Math.sqrt(G * M / r);
		    double vx    = -speed * Math.sin(angle);
		    double vy    =  speed * Math.cos(angle);

		    totalMomentumX += mass * vx;
		    totalMomentumY += mass * vy;

		    planetBodies.add(new Body(
		        new Vector2D(px, py),
		        new Vector2D(vx, vy),
		        mass, 4
		    ));
		}

		// Give the star the exact opposite momentum so total system momentum = 0
		double starVx = -totalMomentumX / M;
		double starVy = -totalMomentumY / M;

		Body star = new Body(
		    new Vector2D(0, 0),
		    new Vector2D(starVx, starVy),
		    M, 10
		);

		sim.addBody(star);
		for (Body b : planetBodies) {
		    sim.addBody(b);
		}

		sim.initialize();
		
		
        SimulationRenderer renderer = new SimulationRenderer();
        renderer.init(); 
        
        while (!renderer.shouldClose()) {
            sim.step();
            renderer.render(sim);
        }
        
        renderer.cleanup();
        
		} catch (Throwable t) {
            System.err.println("Crash: " + t.getClass().getName());
            System.err.println("Message: " + t.getMessage());
            t.printStackTrace();
        }
	}
	
	
}
