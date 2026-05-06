package com.gravitysim.core;

public class Vector2D {
	public double x;  // horizontal component
	public double y;  // vertical component
	
	public Vector2D(double x, double y) {
		this.x = x;
		this.y = y;
	}
	
	//Vector Manipulation
	public static Vector2D zero() { 
		return new Vector2D(0,0); 
	}
	
	public Vector2D add(Vector2D other) { 
		return new Vector2D(this.x + other.x, this.y + other.y); 
	}
	
	public Vector2D subtract(Vector2D other) { 
		return new Vector2D(this.x - other.x, this.y - other.y); 
	}
	
	public Vector2D scale(double s) { 
		return new Vector2D(this.x * s, this.y * s); 
	}
	
	public Vector2D negate() { 
		return new Vector2D(-this.x, -this.y); 
	} 
	
	//Vectore getters
	public double magnitude() { 
		return Math.sqrt(x*x + y*y); 
	}
	
	public double magnitudeSquared() { 
		return x*x + y*y; 
	}
	
	public double distanceTo(Vector2D other) {
		double dx = this.x - other.x;
		double dy = this.y - other.y;
		return Math.sqrt(dx*dx + dy*dy);
	}
	
	public Vector2D normalize() {
		double mag = magnitude();
		if (mag == 0) { return Vector2D.zero(); }
		return scale(1.0 / mag);
	}
	
	public double dot(Vector2D other) {
		return this.x * other.x + this.y * other.y;
	}
	
	public Vector2D copy() {
		return new Vector2D(this.x, this.y);
	}
	
	@Override
	public String toString() {
		return String.format("(%.3f, %.3f)", x, y);
	}
	
}

