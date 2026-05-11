package com.gravitysim.core;

public class BodyFactory {
	//state machine
	public enum State {
		IDLE,
		CONFIRMING,
		CONFIGURING,
		DRAGGING,
		VELOCITY_SET
	}

	private State state = State.IDLE;
	
	
	//click position where the body will be place
	private float clickWorldX, clickWorldY;
	private float clickScreenX, clickScreenY;
	
	//Velocity dragging
	private float dragEndWorldX, dragEndWorldY;
	private boolean isDragging = false;
	
	//new body properties
	public String pendingName = "";
	public float pendingMass = 1e24f;
	public float pendingRadius = 6.4e9f;
	public boolean pendingTrackable = false;
	
	/* Velocity Scale 
	 * Tuned so a full drag across the screen will result
	 * in an orbital velocity roughly similar to that of earh
	 */
	public static final double VELOCITY_DRAG_SCALE = 2000.0;
	
	//state getters
	public State getState() { return state; }
	public boolean isIdle() { return state == State.IDLE; }
	public boolean isConfirming() { return state == State.CONFIRMING; }
	public boolean isConfiguring() { return state == State.CONFIGURING;	}
	public boolean isDragging() { return state == State.DRAGGING; }
	public boolean isVelocitySet() {return state == State.VELOCITY_SET; }
	
	//position and velocity getters
	public float getClickWorldX() { return clickWorldX; }
	public float getClickWorldY() { return clickWorldY; }
	public float getClickScreenX() { return clickScreenX; }
	public float getClickScreenY() { return clickScreenY; }
	public float getDragEndWorldX() { return dragEndWorldX; }
	public float getDragEndWorldY() { return dragEndWorldY; }
	
	//compute velocity from drag
	public double getVelocityX() {
		return (dragEndWorldX - clickWorldX) * VELOCITY_DRAG_SCALE;
	}

	public double getVelocityY() {
		return (dragEndWorldY - clickWorldY) * VELOCITY_DRAG_SCALE;
	}
	
	//clicking methods
	//called when left click is registered in IDLE state
	public void onWorldClick(float worldX, float worldY, float screenX, float screenY) {
		if (state != State.IDLE) { return; }
//		System.out.println("onWorldClick - worldX: " + worldX + " worldY: " + worldY);
		clickWorldX = worldX;
		clickWorldY = worldY;
		clickScreenX = screenX;
		clickScreenY = screenY;
		state = State.CONFIRMING;
	}
	
	//Called when confirmed is clicked in the confirmation window
	public void confirmPlacement() {
		if (state != State.CONFIRMING) { return; }
		state = State.CONFIGURING;
	}
	
	//Called when clicking outside the confirmation window
	public void cancelConfirmation() { 
		if (state != State.CONFIRMING) { return; }
		reset();
	}
	
	//Called when mouse press begins while in configuring
	public void startDrag(float worldX, float worldY) {
		if (state != State.CONFIGURING) { 
//			System.out.println("  -> guard failed, returning");
			return; 
		}
		dragEndWorldX = worldX;
		dragEndWorldY = worldY;
		isDragging = true;
		state = State.DRAGGING;
	}
	
	//called every frame while you're dragging for initial velocity
    public void updateDrag(float worldX, float worldY) {
        if (state != State.DRAGGING) { return; }
        dragEndWorldX = worldX;
        dragEndWorldY = worldY;
    }
	
	//called on mouse release WHILE dragging
	public void endDrag() {
		if (state != State.DRAGGING) { return; }
		isDragging = false;
		state = State.VELOCITY_SET;
	}
	
	//called when velocity is cancelled.  Returns to configuring
	public void retryVelocity() {
		if (state != State.VELOCITY_SET) { return; }
		dragEndWorldX = clickWorldX;
		dragEndWorldY = clickWorldY;
		state = State.CONFIGURING;
	}
	
	//called when the final confirm is hit for the new body
	public Body buildAndCreate() {
		if (state != State.VELOCITY_SET) return null;
		String name = pendingName.isEmpty() ? null : pendingName;
		// Cast to double before dividing to preserve precision

//	    System.out.println("placing body at: " + posX + ", " + posY);
		Body body = new Body(
			new Vector2D(clickWorldX / 1e-11f, clickWorldY / 1e-11f),
			new Vector2D(getVelocityX(), getVelocityY()),
			pendingMass,
			pendingRadius,
			name,
			pendingTrackable
		);
		reset();
		return body;
	}
		
	//reset method to return to idle
	public void reset() {
		state = State.IDLE;
		pendingName = "";
		pendingMass = 1e24f;
		pendingRadius = 6.4e9f;
		pendingTrackable = false;
		dragEndWorldX = 0;
		dragEndWorldY = 0;
		isDragging = false;
	}		
}
	
	
