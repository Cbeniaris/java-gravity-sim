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
	
	public void initialize() {
		tree.build(bodies);
		computeAccelerations();
	}
	
	//simulation loop
	
	public void step() {
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
	}
	
	private void computeAccelerations() {
		for (Body b : bodies) {
			b.resetAcceleration();
			Vector2D force = tree.calculateForce(b, G, epsilon);
			b.acceleration = force.scale(1.0 / b.mass);
		}
	}
	
	// Setters / getters
	
	public List<Body> getBodies() { return bodies; }
	public QuadTree getTree() { return tree; }
	public int getBodyCount() { return bodies.size(); }
	public void setTheta(double theta) { tree.setTheta(theta); }
}
