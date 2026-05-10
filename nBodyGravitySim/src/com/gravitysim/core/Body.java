package com.gravitysim.core;

public class Body {
	public Vector2D position;
	public Vector2D velocity;
	public Vector2D acceleration;
	public double mass;
	public double radius;
	public String name;
	public boolean trackable = false;
	
	public Body(Vector2D position, Vector2D velocity, double mass, double radius, String name, boolean trackable) {
		this.position = position;
		this.velocity = velocity;
		this.acceleration = Vector2D.zero();
		this.mass = mass;
		this.radius = radius;
		this.name = name;
		this.trackable = trackable;
	}
	
	public Body(Vector2D position, Vector2D velocity, double mass, double radius) {
		this.position = position;
		this.velocity = velocity;
		this.acceleration = Vector2D.zero();
		this.mass = mass;
		this.radius = radius;
	}
	
	public void resetAcceleration() {
		this.acceleration = Vector2D.zero();
	}
	
	public void update(double dt) {
		//advance the velocity by a half step
		velocity = velocity.add(acceleration.scale(0.5 * dt));
		
		//advance position by a full time step 
		position = position.add(velocity.scale(dt));
		
		//Second half step happens at the start of the next fram after forces are calculated
		//See Simulation.step()
	}
	
	public void halfKick(double dt) {
		velocity = velocity.add(acceleration.scale(0.5 * dt));
	}
	
	public String getDisplayName() {
		return (name != null && !name.isEmpty()) ? name : "Unnamed Body";
	}
}
