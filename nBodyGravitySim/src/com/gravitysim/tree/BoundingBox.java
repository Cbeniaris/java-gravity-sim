package com.gravitysim.tree;

import com.gravitysim.core.Vector2D;

public class BoundingBox {
	public double x; // center x
	public double y; // center y
	public double halfWidth; // half the length

	public BoundingBox(double x, double y, double halfWidth) {
		this.x = x;
		this.y = y;
		this.halfWidth = halfWidth;
	}
	
	public boolean contains(Vector2D point) {
		return point.x >= x - halfWidth &&
				point.x < x + halfWidth &&
				point.y >= y - halfWidth &&
				point.y < y + halfWidth;
	}

	public BoundingBox[] subdivide() {
		double half = halfWidth/2.0;
		return new BoundingBox[] {
				new BoundingBox(x-half, y+half, half), // NW quadrant
				new BoundingBox(x+half, y+half, half), // NE quadrant
				new BoundingBox(x-half, y-half, half), // SW quadrant
				new BoundingBox(x+half, y-half, half) // SE quadrant
		};
	}
	
	public boolean intersects(BoundingBox other) {
		return Math.abs(this.x - other.x) < this.halfWidth + other.halfWidth &&
				Math.abs(this.y - other.y) < this.halfWidth + other.halfWidth;
	}
	
	public double size() {
		return halfWidth * 2.0;
	}
	
	@Override
	public String toString() {
		return String.format("BoundingBox[center=%.1f, %.1f half = %.1f]",  x, y, halfWidth);
	}
}
