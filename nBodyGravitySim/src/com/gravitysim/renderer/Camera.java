package com.gravitysim.renderer;

import org.joml.Matrix4f;

public class Camera {

	public float x;
	public float y;
	public float zoom;
	
	public Camera() {
		this.x = 0;
		this.y = 0;
		this.zoom = 1.0f;
	}
	
	public void pan(float dx, float dy) {
		x += dx/zoom;
		y += dy/zoom;
	}
	
	public void zoom(float factor) {
		zoom *= factor;
		zoom = Math.max(1e-10f,  Math.min(zoom,  1e10f));
	}
	
	public Matrix4f getViewMatrix() {
		return new Matrix4f().scale(zoom, zoom, 1.0f).translate(-x, -y, 0.0f);
	}
	
	public Matrix4f getProjectionMatrix(int width, int height) {
		float aspect = (float) width / height;
		float viewSize = 20.0f / zoom;   // visible world units — tweak this value
		return new Matrix4f().ortho(
		        -aspect * viewSize,  aspect * viewSize,
		        -viewSize,           viewSize,
		        -1.0f, 1.0f
		    );
	}
}
