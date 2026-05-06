package com.gravitysim.tree;

import com.gravitysim.core.Body;
import com.gravitysim.core.Vector2D;

public class QuadTreeNode {

	private static final int EMPTY = 0;
	private static final int LEAF = 1;
	private static final int INTERNAL = 2;
	
	public BoundingBox boundary;
	public double totalMass;
	public Vector2D centerOfMass;
	public Body body; // non-null ONLY when LEAF
	public QuadTreeNode[] children; // non-null ONLY when INTERNAL
	
	private int state;
	
	public QuadTreeNode(BoundingBox boundary) {
		this.boundary = boundary;
		this.totalMass = 0;
		this.centerOfMass = Vector2D.zero();
		this.body = null;
		this.children = null;
		this.state = EMPTY;
	}
	
	// -------------------------------------------------------------------------------------
	
	// State queries
	public boolean isEmpty() { return state == EMPTY; }
	public boolean isLeaf() { return state == LEAF; }
	public boolean isInternal() { return state == INTERNAL; }

	// -------------------------------------------------------------------------------------
	 
	//Insertion
	public void insert(Body b) {
		if (!boundary.contains(b.position)) return;
	
		if (isEmpty()) {
			body = b;
			totalMass = b.mass;
			centerOfMass = b.position.copy();
			state = LEAF;
			return;
		}
		
		if (isLeaf()) {
			//two bodies share this region, so we subdivide
			subdivide();
			
			//reinsert the body that was there already
			insertIntoChild(body);
			
			//clear direct body reference since it's an internal node now
			body = null;
			state = INTERNAL;
		}
		
		insertIntoChild(b);
		updateMassDistribution(b);
		
	}

	private void insertIntoChild(Body b) {
		for (QuadTreeNode child : children) {
			if (child.boundary.contains(b.position)) {
				child.insert(b);
				return;
			}
		}
	}
	
	private void subdivide() {
		BoundingBox[] boxes = boundary.subdivide();
		children = new QuadTreeNode[] {
				new QuadTreeNode(boxes[0]),
				new QuadTreeNode(boxes[1]),
				new QuadTreeNode(boxes[2]),
				new QuadTreeNode(boxes[3])
		};
	}
	
	
	// -------------------------------------------------------------------------------------
	 
	//Mass Distributions
	private void updateMassDistribution(Body b) {
		double newMass = totalMass + b.mass;
		double newCOMx = (centerOfMass.x * totalMass + b.position.x * b.mass) / newMass; // find the x coordinate of the new COM
		double newCOMy = (centerOfMass.y * totalMass + b.position.y * b.mass) / newMass; // find the y coordinate of the new COM
		totalMass = newMass;
		centerOfMass = new Vector2D(newCOMx, newCOMy);
	}
	
	// -------------------------------------------------------------------------------------
	 
	//Force calculations
	public Vector2D calculateForce(Body b, double theta, double G, double epsilon) {
		if (isEmpty()) return Vector2D.zero();
		
		if (isLeaf()) {
			if (body == b)  { return Vector2D.zero(); } //skip self
			return computeForce(b, centerOfMass, totalMass, G, epsilon);
		}
		
		// Internal Node = Barnes-Hut theta Check
		double s = boundary.size();
		double d = b.position.distanceTo(centerOfMass);
	
		 // sanity check for d = 0.  When d is zero, the following theta check will explode to into infinity
	    if (d < 1e-10) {
	        Vector2D force = Vector2D.zero();
	        for (QuadTreeNode child : children) {
	            force = force.add(child.calculateForce(b, theta, G, epsilon));
	        }
	        return force;
	    }
		
		if (s / d < theta) {
			return computeForce(b, centerOfMass, totalMass, G, epsilon);
		}
	
		//Too close = recursive children calculation
		Vector2D force = Vector2D.zero();
		for (QuadTreeNode child : children) {
			force = force.add(child.calculateForce(b,theta,G,epsilon));
		}
		return force;
		
		
		
	}

	private Vector2D computeForce(Body b, Vector2D otherPos, double otherMass, double G, double epsilon) {
		Vector2D direction = otherPos.subtract(b.position);
		double dist = direction.magnitude();
		double distSoft = Math.sqrt(dist*dist + epsilon * epsilon);
		double magnitude = (G * b.mass * otherMass) / (distSoft * distSoft * distSoft);
		
		//magnitude / distSoft ^ 3 combineed with direction = force vector directly without a normalize step
		return direction.scale(magnitude);
	}
}
