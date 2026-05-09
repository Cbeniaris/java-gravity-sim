package com.gravitysim;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
		Random rand = new Random(42); // fixed seed for reproducibility

		// Planet orbital parameters — {radius in AU, mass in kg, start angle in degrees, Body Radius}
		double[][] planets = {
		    { 0.4 * 1.496e11, 3.3e23,   0,   2.5e9 },  // Mercury
		    { 0.7 * 1.496e11, 4.87e24,  30,  6.0e9 },  // Venus
		    { 1.0 * 1.496e11, 5.97e24,  60, 6.4e9 },  // Earth
		    { 1.5 * 1.496e11, 6.39e23,  90, 3.4e9 },  // Mars
		    { 5.2 * 1.496e11, 1.898e27, 120,  7.0e9 },  // Jupiter
		    { 9.58 * 1.496e11, 5.68e26, 150,  5.8232e9 },  // Saturn
		    { 19.2 * 1.496e11, 8.68e25, 180,  2.5559e9 },  // Uranus
		    { 30.1 * 1.496e11, 1.02e26, 210,  2.4764e9 },  // Neptune
		    { 40 * 1.496e11, 1.31e22, 240,  1.15e8 },  // Pluto
		};

		// Calculate total momentum of all planets first
		double totalMomentumX = 0;
		double totalMomentumY = 0;

		List<Body> planetBodies = new ArrayList<>();
		for (double[] p : planets) {
		    double r = p[0];
		    double mass = p[1];
		    double angle = Math.toRadians(p[2]);
		    double radius = p[3];

		    // Position around the star
		    double px = r * Math.cos(angle);
		    double py = r * Math.sin(angle);

		    // Circular orbit velocity — perpendicular to radius
		    double speed = Math.sqrt(G * M / r);
		    double vx = -speed * Math.sin(angle);
		    double vy =  speed * Math.cos(angle);

		    totalMomentumX += mass * vx;
		    totalMomentumY += mass * vy;

		    planetBodies.add(new Body(
		        new Vector2D(px, py),
		        new Vector2D(vx, vy),
		        mass, radius
		    ));
		}
		
		// Asteroid belt — between Mars (1.5 AU) and Jupiter (5.2 AU)
		// Real belt is roughly 2.2 to 3.2 AU
		int asteroidCount = 2000;
		for (int i = 0; i < asteroidCount; i++) {
		    // Random radius between 2.2 and 3.2 AU
		    double r     = (2.2 + rand.nextDouble() * 1.0) * 1.496e11;

		    // Random angle spread around the belt
		    double angle = rand.nextDouble() * 2 * Math.PI;

		    // Small eccentricity — slightly elliptical orbits
		    // achieved by perturbing the velocity by +-5%
		    double speed       = Math.sqrt(G * M / r);
		    double perturbation = 1.0 + (rand.nextDouble() - 0.5) * 0.05;
		    speed *= perturbation;

		    double px = r * Math.cos(angle);
		    double py = r * Math.sin(angle);
		    double vx = -speed * Math.sin(angle);
		    double vy =  speed * Math.cos(angle);

		    // Asteroid mass — tiny, negligible gravitational effect
		    double mass = 1e12 + rand.nextDouble() * 1e15;

		    totalMomentumX += mass * vx;
		    totalMomentumY += mass * vy;

		    planetBodies.add(new Body(
		        new Vector2D(px, py),
		        new Vector2D(vx, vy),
		        mass, 5e8  // small but visible
		    ));
		}

		// Give the star the exact opposite momentum so total system momentum = 0
		double starVx = -totalMomentumX / M;
		double starVy = -totalMomentumY / M;

		Body star = new Body(
		    new Vector2D(0, 0),
		    new Vector2D(starVx, starVy),
		    M, 5.0e10
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
