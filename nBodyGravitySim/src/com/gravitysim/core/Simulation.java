package com.gravitysim.core;

import com.gravitysim.tree.QuadTree;

import java.util.ArrayList;
import java.util.List;

public class Simulation {
	
	private List<Body> bodies;
	private QuadTree tree;
	
	public double G;
	public double epsilon;
	public double dt;
	
	private boolean paused = false;
	private List<Body> initialBodies; // snapshot for reset

	
	public Simulation(double G, double epsilon, double dt) {
		this.G = G;
		this.epsilon = epsilon;
		this.dt = dt;
		this.bodies = new ArrayList<>();
		this.tree = new QuadTree(0.5, 1000);
	}
	
	//Initializing methods
	
	public void addBody(Body b) {
		bodies.add(b);
	}
	
	public void addBodyLive(Body b) {
		bodies.add(b);
//		if (b.trackable) {
//    		System.out.println("Adding Body: " + b.name);
//    		System.out.println("x: " + b.position.x);
//    		System.out.println("y: " + b.position.y);
//    	}
		tree.build(bodies);
		computeAccelerations();
	}
	
	public void initialize() {
		// Store a deep copy of initial state for reset
		initialBodies = new ArrayList<>();
	    for (Body b : bodies) {
	    	initialBodies.add( new Body(
	            new Vector2D(b.position.x, b.position.y),
	            new Vector2D(b.velocity.x, b.velocity.y),
	            b.mass, 
	            b.radius,
	            b.name,
	            b.trackable
	        ));    	
	    }
	    tree.build(bodies);
	    computeAccelerations();
	}
	
	//simulation loop
	
	public void step() {
		//check if pause
		if (paused) { return; }
		
		//step 1 : halfkick
		for (Body b : bodies) {
			b.halfKick(dt);
		}
		
		//step 2 : drift all positions foward
		for (Body b : bodies) {
			b.position = b.position.add(b.velocity.scale(dt));
		}
		
		//step 3 : rebuild the tree with new positions
		tree.build(bodies);
		
		//step 4 : recompute accelerations from new positions
		computeAccelerations();
		
		//step 5 : second halfkick with new accelerations
		for (Body b : bodies) {
			b.halfKick(dt);
		}
		
		// Record trail positions for trackable bodies
		for (Body b : bodies) {
		    if (b.trackable) {
//		    	System.out.println("adding trail for: " + b.getDisplayName());
		        b.trail.addLast(b.position.copy());
		        if (b.trail.size() > Body.TRAIL_LENGTH) {
		            b.trail.removeFirst();
		        }
		    }
		}
	}
	
	private void computeAccelerations() {
		for (Body b : bodies) {
			b.resetAcceleration();
			Vector2D force = tree.calculateForce(b, G, epsilon);
			b.acceleration = force.scale(1.0 / b.mass);
		}
	}
	
	public void reset() {
		bodies.clear();
		for (Body b : initialBodies) {
			Body copy = new Body(
				new Vector2D(b.position.x, b.position.y),
	            new Vector2D(b.velocity.x, b.velocity.y),
				b.mass,
				b.radius,
				b.name,
				b.trackable
			); 
			copy.trail.clear();
			bodies.add(copy);
		}
		paused = false;
		tree.build(bodies);
		computeAccelerations();
	}
	
	public void clear() {
		bodies.clear();
	    tree.build(bodies);
	    paused = false;
	}
	
	// Setters / getters
	
	public List<Body> getBodies() { return bodies; }
	public QuadTree getTree() { return tree; }
	public int getBodyCount() { return bodies.size(); }
	public void setTheta(double theta) { tree.setTheta(theta); }
	public boolean isPaused() { return paused; }
	public void togglePause() { paused = !paused; }

	
}
