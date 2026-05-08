package com.gravitysim.tree;

import com.gravitysim.core.Body;
import com.gravitysim.core.Vector2D;

import java.util.List;

public class QuadTree {
	
	private QuadTreeNode root;
	private double theta;
	private double rootSize;
	
	public QuadTree(double theta, double rootSize) {
		this.theta = theta;
		this.rootSize = rootSize;
	}
	
	//---------------------------------------------------------
	
	//Building the Quad Tree
	public void build(List<Body> bodies) {
		BoundingBox boundary = computeBoundary(bodies);
		root = new QuadTreeNode(boundary);
		for (Body b : bodies) {
			root.insert(b);
		}
	}
	
	//---------------------------------------------------------
	
	//force calculation
	public Vector2D calculateForce(Body b, double G, double epsilon) {
		if (root == null) { return Vector2D.zero(); }
		return root.calculateForce(b, theta, G, epsilon);
	}

	private BoundingBox computeBoundary(List<Body> bodies) {
		if (bodies.isEmpty()) {
			return new BoundingBox(0,0,rootSize);
		}
		
		double minX = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE;
		double minY = Double.MAX_VALUE;
		double maxY= -Double.MAX_VALUE;
	
		//appropriately set the values for the max x,y
		for (Body b : bodies) {
			if (b.position.x < minX) minX = b.position.x;
            if (b.position.x > maxX) maxX = b.position.x;
            if (b.position.y < minY) minY = b.position.y;
            if (b.position.y > maxY) maxY = b.position.y;
		}
		
		double centerX = (minX + maxX) / 2.0;
		double centerY = (minY + maxY) / 2.0;
		double halfWidth = Math.max(maxX - minX,  maxY - minY) / 2.0 * 1.05;
		// 1.05 padding makes sure the bodies on the exact edge are sfely inside the boundary after floating point math
		halfWidth = Math.max(halfWidth, rootSize);  // never collapse to zero
		
		return new BoundingBox(centerX, centerY, halfWidth);
	}
	//---------------------------------------------------------
	
	//Getters/setters
	public QuadTreeNode getRoot() { return root; }
	public double getTheta() { return theta; }
	public void setTheta(double theta) { this.theta = theta; }
	
}
