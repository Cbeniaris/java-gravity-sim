package com.gravitysim.renderer;

import com.gravitysim.core.Body;
import com.gravitysim.core.BodyFactory;
import com.gravitysim.core.Simulation;
import com.gravitysim.core.SimulationIO;
import com.gravitysim.core.Vector2D;
import com.gravitysim.tree.*;
import org.joml.Matrix4f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import imgui.ImGui;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import imgui.type.ImBoolean;


import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.system.MemoryUtil.NULL;



public class SimulationRenderer {

	//debug flags
	public boolean showVelocityVectors = false;
	public boolean showAccelerationVectors = false;
	public boolean showQuadTree = false;
	
	
	//window
	private long windowHandle;
	private int windowWidth = 1280;
	private int windowHeight = 720;
	
	//camera and input states
	private Camera camera;
	private double lastMouseX, lastMouseY;
	private boolean mouseDown = false;
	
	//GL resources
	private int shaderProgram;
	private int vao;
	private int vbo;
	
	//scale constants
	private static final float VELOCITY_SCALE = 4e6f;
	private static final float ACCELERATION_SCALE = 1e12f;
	private static final float WORLD_SCALE = 1e-11f;
	
	//ImGui
	private ImGuiImplGlfw imguiGlfw;
	private ImGuiImplGl3 imguiGl3;
	private boolean showUI = false;
	
	//Background image
	private int backgroundTexture;
	private int backgroundVao;
	private int backgroundVbo;
	private int backgroundShader;
	
	//saving and loading
	private imgui.type.ImString saveFilePath = new imgui.type.ImString("simulation", 128);
	private String saveStatusMessage = "";
	
	//body factory
	private BodyFactory bodyFactory = new BodyFactory();
	private boolean justCommittedBody = false;
	
	//body trails
	public boolean showTrails = true;
	private static final int ORBIT_PROJECTION_STEPS = 5000;
	
	//Tracking Indicators
	private static final float TRACKING_THRESHOLD_PX = 8f;
	
	//init
	public void init(Simulation sim) {
		// Force X11 backend instead of Wayland
		org.lwjgl.glfw.GLFW.glfwInitHint(
		    org.lwjgl.glfw.GLFW.GLFW_PLATFORM, 
		    org.lwjgl.glfw.GLFW.GLFW_PLATFORM_X11);
		if (!glfwInit()) { throw new IllegalStateException("GLFW init failed"); }
		glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
		glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
		glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE); // required on macOS

		windowHandle = glfwCreateWindow(windowWidth, windowHeight, "Gravity Sim", NULL, NULL);
		
		if (windowHandle == NULL) throw new RuntimeException("Window creation failed");
		
		glfwMakeContextCurrent(windowHandle);
		glfwSwapInterval(1); //vsync
		GL.createCapabilities();
		
		camera = new Camera();
		
		setupCallbacks(sim);
		setupShaders();
		setupBuffers();
		
		
		glEnable(GL_BLEND);
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
		
		setupBackground();
		backgroundTexture = loadTexture("assets/background.jpg");
		
		ImGui.createContext();
		imguiGlfw = new ImGuiImplGlfw();
		imguiGl3 = new ImGuiImplGl3();
		imguiGlfw.init(windowHandle, true);
		imguiGl3.init("#version 330 core");
	}
	
	//RENDERERS ------------------------------------------------------------------------------------------------------------------------
	
	//main render call
	public void render(Simulation sim) {
		glClearColor(0.05f, 0.05f, 0.08f, 1.0f);
		glClear(GL_COLOR_BUFFER_BIT);
		
		//render the background
		renderBackground();
		
		Matrix4f view = camera.getViewMatrix();
		Matrix4f proj = camera.getProjectionMatrix(windowWidth, windowHeight);
		Matrix4f mvp = proj.mul(view, new Matrix4f());
		
		glUseProgram(shaderProgram);
		setUniformMatrix(mvp);

		renderBodies(sim.getBodies());
		if(showTrails) { renderTrails(sim.getBodies()); }
		if (showVelocityVectors) { renderVelocityVectors(sim.getBodies()); }
		if (showAccelerationVectors) { renderAccelerationVectors(sim.getBodies()); }
		if (showQuadTree) { renderQuadTree(sim.getTree().getRoot()); }

		imguiGlfw.newFrame();
	    ImGui.newFrame();
		
		renderUI(sim);
		renderBodyFactory(sim);
		renderTrackingIndicators(sim.getBodies());
        
		ImGui.render();
		imguiGl3.renderDrawData(ImGui.getDrawData());
        glfwSwapBuffers(windowHandle);
        glfwPollEvents();
	}
	
	//body rendering
	private void renderBodies(List<Body> bodies) {
		int segments = 48; // 48 triangles per circle 

	    // Pre-calculate total float count so we allocate once
	    int totalFloats = bodies.size() * segments * 9; // 3 verts * 3 floats per circle segment
	    float[] verts = new float[totalFloats];
	    int offset = 0;

	    for (Body b : bodies) {
	        float cx = (float)(b.position.x * WORLD_SCALE);
	        float cy = (float)(b.position.y * WORLD_SCALE);
	        float radius = (float)(b.radius * WORLD_SCALE);
	        
	        // How many world units correspond to 1 pixel at current zoom
	        float viewSize = 20.0f / camera.zoom;
	        float worldUnitsPerPixel = (viewSize * 2) / windowWidth;
	        float minRadius = 1.5f * worldUnitsPerPixel; // minimum 1.5 pixels on scree
	        
	        radius = Math.max(radius, minRadius);
	        
	        float[] circle = generateCircle(cx, cy, radius, segments);
	        System.arraycopy(circle, 0, verts, offset, circle.length);
	        offset += circle.length;
	    }

	    uploadAndDraw(verts, GL_TRIANGLES, 1.0f, 1.0f, 1.0f, 1.0f);
	}
	
	//Render UI
    private void renderUI(Simulation sim) {
    	//check if the UI is hidden or being shown
    	if (!showUI) {
            // Show only the small toggle button when hidden
            ImGui.setNextWindowPos(10, 10, imgui.flag.ImGuiCond.Always);
            ImGui.setNextWindowSize(90, 30, imgui.flag.ImGuiCond.Always);
            ImGui.begin("##toggle_collapsed",
                new ImBoolean(true),
                imgui.flag.ImGuiWindowFlags.NoTitleBar |
                imgui.flag.ImGuiWindowFlags.NoResize |
                imgui.flag.ImGuiWindowFlags.NoScrollbar |
                imgui.flag.ImGuiWindowFlags.NoCollapse |
                imgui.flag.ImGuiWindowFlags.NoMove
            );
            if (ImGui.button("Controls", 74, 18)) {
                showUI = true;
            }
            ImGui.end();
            return;
        }
    
    	
    	//pin the window to the top left corner
    	ImGui.setNextWindowPos(10, 10, imgui.flag.ImGuiCond.Always);
    	ImGui.setNextWindowSize(410, 420, imgui.flag.ImGuiCond.Always);
    	ImGui.begin("Simulation Controls",
    		imgui.flag.ImGuiWindowFlags.NoCollapse |
    		imgui.flag.ImGuiWindowFlags.NoResize |
    		imgui.flag.ImGuiWindowFlags.NoMove
    		);
    	
    	//Hide UI button
    	ImGui.sameLine();
    		if (ImGui.button("Hide UI", 74, 18)) {
            showUI = false;
        }
    	
    	//simulation text
    	ImGui.text("Simulation");
    	
    	//time slider
    	float[] dt = { (float) sim.dt };
    	if (ImGui.sliderFloat("Timestep (s)", dt, 3600f, 604800)) {
    		sim.dt = dt[0];
    	}
    	ImGui.sameLine();
    	ImGui.textDisabled("(?)");
    	if (ImGui.isItemHovered()) {
    		ImGui.setTooltip("Seconds simulated per step. \n 3600 = 1 hour, 86400 = 1 day");
    	}
    	
    	//Theta slider
    	float[] theta = { (float) sim.getTree().getTheta()};
    	if (ImGui.sliderFloat("Theta", theta, 0.1f, 2.0f)) {
    		sim.setTheta(theta[0]);
    	}
    	ImGui.sameLine();
    	ImGui.textDisabled("(?)");
    	if (ImGui.isItemHovered()) {
    		ImGui.setTooltip("Barnes-Hut accuracy threshold. \n Lower = more accurate, more computing \n Higher = less accurate, faster");
    	}
    	
    	//Pause, reset, and hide Button
    	ImGui.spacing();
        if (ImGui.button(sim.isPaused() ? "Resume" : "Pause", 120, 24)) {
            sim.togglePause();
        }
        ImGui.sameLine();
        if (ImGui.button("Reset", 120, 24)) {
            sim.reset();
        }
        
        // Save and Load
        ImGui.spacing();
        ImGui.separator();
        ImGui.textDisabled("SAVE / LOAD");
        ImGui.separator();
        ImGui.spacing();
        
        if (ImGui.button("Save",84, 24)) {
        	java.io.File file = openFileChooser(true);
        	if (file != null) {
	        	try {
	        		file.getParentFile().mkdirs();
	                SimulationIO.save(sim, file.getAbsolutePath());
	                saveStatusMessage = "Saved: " + file.getName();
	        	} catch (Exception e){
	        		saveStatusMessage = "Save Failed: " + e.getMessage();
	        	}
	        }
        }
        
        ImGui.sameLine();
        if (ImGui.button("Load", 84, 24)) {
    	 java.io.File file = openFileChooser(false);
    	    if (file != null) {
	            try {
	            	SimulationIO.load(sim, file.getAbsolutePath());
	                saveStatusMessage = "Loaded: " + file.getName();
	            } catch (Exception e) {
	                saveStatusMessage = "Load failed: " + e.getMessage();
	            }
	        }
        }
        ImGui.sameLine();
        if (ImGui.button("Clear", 84, 24)) {
            sim.clear();
            saveStatusMessage = "Simulation cleared";
        }

        if (!saveStatusMessage.isEmpty()) {
            ImGui.spacing();
            ImGui.textDisabled(saveStatusMessage);
        }
        
        
    	//Debug Overlays
        ImGui.spacing();
        ImGui.text("Debug Overlays");
        
        ImBoolean velVec = new ImBoolean(showVelocityVectors);
        ImBoolean accVec = new ImBoolean(showAccelerationVectors);
        ImBoolean qtree = new ImBoolean(showQuadTree);
        ImBoolean trails = new ImBoolean(showTrails);
        
        
        if (ImGui.checkbox("Velocity Vectors", velVec))
            showVelocityVectors = velVec.get();
        if (ImGui.checkbox("Acceleration Vectors", accVec))
            showAccelerationVectors = accVec.get();
        if (ImGui.checkbox("Quadtree Grid", qtree))
            showQuadTree = qtree.get();
        if (ImGui.checkbox("Orbital Trails", trails))
            showTrails = trails.get();
       
        //Stats
       
        ImGui.spacing();
        ImGui.text("Stats");
        ImGui.text("Bodies : " + sim.getBodyCount());
        ImGui.text(String.format("dt: %.0f s (%.2f days)", sim.dt, sim.dt / 86400.0));
        ImGui.text(String.format("Theta: %.2f", sim.getTree().getTheta()));
        ImGui.text(String.format("Zoom: %.4f", camera.zoom));
        
        ImGui.end();    
    }
    
    //Background Rendering
  	private void renderBackground() {
  		glUseProgram(backgroundShader);
  		glBindVertexArray(backgroundVao);
  		glActiveTexture(GL_TEXTURE0);
  		glBindTexture(GL_TEXTURE_2D, backgroundTexture);
  		glUniform1i(glGetUniformLocation(backgroundShader, "uTexture"), 0);
  	    glDrawArrays(GL_TRIANGLES, 0, 6);
  	    glBindVertexArray(0);
  	}
	
	private void renderCircle(double worldX, double worldY, float r, float g, float b, float alpha){
		int segments = 24;
	    float cx     = (float)(worldX);
	    float cy     = (float)(worldY);

	    // Minimum visible radius regardless of zoom
	    float viewSize      = 20.0f / camera.zoom;
	    float worldPerPixel = (viewSize * 2.0f) / windowWidth;
	    float radius        = Math.max((float)(bodyFactory.pendingRadius * WORLD_SCALE),
	                                    3.0f * worldPerPixel);

	    float[] verts = new float[segments * 9];
	    for (int i = 0; i < segments; i++) {
	        double angle1 = 2 * Math.PI * i       / segments;
	        double angle2 = 2 * Math.PI * (i + 1) / segments;
	        int idx = i * 9;
	        verts[idx]     = cx; verts[idx+1] = cy; verts[idx+2] = 0;
	        verts[idx+3]   = cx + (float)(radius * Math.cos(angle1));
	        verts[idx+4]   = cy + (float)(radius * Math.sin(angle1));
	        verts[idx+5]   = 0;
	        verts[idx+6]   = cx + (float)(radius * Math.cos(angle2));
	        verts[idx+7]   = cy + (float)(radius * Math.sin(angle2));
	        verts[idx+8]   = 0;
	    }
	    uploadAndDraw(verts, GL_TRIANGLES, r, g, b, alpha);
	}
	
	//render body factory
	public void renderBodyFactory(Simulation sim) {
		switch (bodyFactory.getState()) {
		
			case CONFIRMING: 
				renderConfirmationWindow();
				// Show faint circle at click position during confirmation
	            renderCircle(bodyFactory.getClickWorldX(), bodyFactory.getClickWorldY(), 0.8f, 0.8f, 0.2f, 0.3f); // faint yellow
				break;
				
			case CONFIGURING:
				renderPropertiesHUD(sim);
				// Show solid preview circle that will update as radius slider changes
	            renderCircle(bodyFactory.getClickWorldX(), bodyFactory.getClickWorldY(), 0.2f, 0.8f, 1.0f, 0.7f); // blue
				break;
			
			case DRAGGING,VELOCITY_SET:
				renderPropertiesHUD(sim);
				// Show solid preview circle that will update as radius slider changes
				renderCircle(bodyFactory.getClickWorldX(), bodyFactory.getClickWorldY(), 0.2f, 0.8f, 1.0f, 0.7f); // blue
				drawVelocityPreview();
				renderProjectedOrbit(sim);
				break;
                
			default:
				break;
		}
	}

	public void renderConfirmationWindow() {
		// Configurable padding from screen edge
	    final float SCREEN_EDGE_BUFFER = 20.0f;
		
	   // Window size
	    final float WINDOW_WIDTH  = 180.0f;
	    final float WINDOW_HEIGHT = 70.0f;
	    
		//small pop up near click position
		float wx = bodyFactory.getClickScreenX();
		float wy = bodyFactory.getClickScreenY();
		
		//get viewport size
		// Get viewport/display size
	    float displayWidth  = ImGui.getIO().getDisplaySizeX();
	    float displayHeight = ImGui.getIO().getDisplaySizeY();
		
	  // Prevent popup from going off the right side
	    if (wx + WINDOW_WIDTH > displayWidth - SCREEN_EDGE_BUFFER) {
	        wx = displayWidth - WINDOW_WIDTH - SCREEN_EDGE_BUFFER;
	    }

	    // Optional: prevent popup from going off bottom edge
	    if (wy + WINDOW_HEIGHT > displayHeight - SCREEN_EDGE_BUFFER) {
	        wy = displayHeight - WINDOW_HEIGHT - SCREEN_EDGE_BUFFER;
	    }

	    // prevent negative positions as well
	    if (wx < SCREEN_EDGE_BUFFER) {
	        wx = SCREEN_EDGE_BUFFER;
	    }

	    if (wy < SCREEN_EDGE_BUFFER) {
	        wy = SCREEN_EDGE_BUFFER;
	    }
	    
	    ImGui.setNextWindowPos(wx+10, wy+10, imgui.flag.ImGuiCond.Always);
		ImGui.setNextWindowSize(WINDOW_WIDTH,WINDOW_HEIGHT, imgui.flag.ImGuiCond.Always);
		ImGui.begin("##confirm_place",
			imgui.flag.ImGuiWindowFlags.NoTitleBar |
            imgui.flag.ImGuiWindowFlags.NoResize |
            imgui.flag.ImGuiWindowFlags.NoMove |
            imgui.flag.ImGuiWindowFlags.NoScrollbar);
		
		ImGui.text("Create new body here?");
		ImGui.spacing();
		if (ImGui.button("Confirm", 78, 22)) {
			bodyFactory.confirmPlacement();
		}
	    ImGui.sameLine();
	    if (ImGui.button("Cancel", 78, 22)) {
	        bodyFactory.cancelConfirmation();
	    }
		ImGui.end();
	}

	private void renderPropertiesHUD(Simulation sim) {
		// Properties HUD
        ImGui.setNextWindowPos(windowWidth - 350, 10,
            imgui.flag.ImGuiCond.Always);
        ImGui.setNextWindowSize(340, 350,
            imgui.flag.ImGuiCond.Always);
        ImGui.begin("New Body",
            imgui.flag.ImGuiWindowFlags.NoCollapse |
            imgui.flag.ImGuiWindowFlags.NoResize |
            imgui.flag.ImGuiWindowFlags.NoMove);

        ImGui.textDisabled("NEW BODY PROPERTIES");
        ImGui.separator();
        ImGui.spacing();
        
        //Name Field
        imgui.type.ImString nameStr = new imgui.type.ImString(bodyFactory.pendingName, 64);
        if (ImGui.inputText("Name", nameStr)) {
            bodyFactory.pendingName = nameStr.get();
        }
        
        //Logarithmic Mass Slider
        // Mass slider — log scale feels more natural for mass
        float[] mass = { (float)Math.log10(bodyFactory.pendingMass) };
        if (ImGui.sliderFloat("Mass", mass, 12f, 32f)) {
            bodyFactory.pendingMass = (float)Math.pow(10, mass[0]);
        }
        ImGui.sameLine();
    	ImGui.textDisabled("(?)");
    	if (ImGui.isItemHovered()) {
    		ImGui.setTooltip("Mass of planet.  Ex: Earth is ~6e24");
    	}
        ImGui.textDisabled(String.format("  %.2e kg", bodyFactory.pendingMass));

        // Radius slider
        float[] radius = { (float)Math.log10(bodyFactory.pendingRadius) };
        if (ImGui.sliderFloat("Radius", radius, 6f, 11f)) {
            bodyFactory.pendingRadius = (float)Math.pow(10, radius[0]);
        }
        ImGui.sameLine();
    	ImGui.textDisabled("(?)");
    	if (ImGui.isItemHovered()) {
    		ImGui.setTooltip("Radius of planet.  Ex: Earth is ~6.5e9");
    	}
        ImGui.textDisabled(String.format("  %.2e m", bodyFactory.pendingRadius));
        

        // Trackable checkbox
        ImBoolean trackable = new ImBoolean(bodyFactory.pendingTrackable);
        if (ImGui.checkbox("Trackable", trackable)) {
            bodyFactory.pendingTrackable = trackable.get();
        }
        
        ImGui.spacing();
        ImGui.separator();
        ImGui.textDisabled("VELOCITY");
        ImGui.separator();
        ImGui.spacing();
        
        if (bodyFactory.isConfiguring()) {
            ImGui.textDisabled("Left click + drag to set");
            ImGui.textDisabled("initial velocity");
        }
        
        if (bodyFactory.isVelocitySet()) {
            ImGui.text(String.format("vx: %.0f m/s",
                bodyFactory.getVelocityX()));
            ImGui.text(String.format("vy: %.0f m/s",
                bodyFactory.getVelocityY()));
            ImGui.spacing();
            if (ImGui.button("Confirm", 95, 24)) {
                Body newBody = bodyFactory.buildAndCreate();
                if (newBody != null) {
                    sim.addBodyLive(newBody);
                    justCommittedBody = true;
                }
            }
            ImGui.sameLine();
            if (ImGui.button("Retry", 95, 24)) {
                bodyFactory.retryVelocity();
            }
        }

        ImGui.spacing();
        if (ImGui.button("Cancel Creation", 200, 22)) {
//        	System.out.println("Cancelling body creation");
            bodyFactory.reset();
        }

        ImGui.end();
	}
	
	//Render Tracking Lines
	private void debugDrawLine() {
	    glUseProgram(shaderProgram);
	    float[] verts = {
	        -5.0f,  0.0f, 0.0f,
	         5.0f,  0.0f, 0.0f
	    };
	    uploadAndDraw(verts, GL_LINES, 1.0f, 0.0f, 0.0f, 1.0f); // bright red
	}
	
	private void renderTrackingIndicators(List<Body> bodies) {
	    // Leader line lengths in screen pixels — converted to world space
	    float viewSize       = 20.0f / camera.zoom;
	    float worldPerPixel  = (viewSize * 2.0f) / windowWidth;

	    float diagonalLen = 20.0f * worldPerPixel;  // length of the NW diagonal
	    float horizontalLen = 30.0f * worldPerPixel; // length of the horizontal segment

	    for (Body b : bodies) {
	        if (!b.trackable) continue;
	        
//	        System.out.println("Drawing trail for: " + b.getDisplayName() + " points: " + b.trail.size());

	        // Calculate screen-space radius to check threshold
	        float worldRadius  = (float)(b.radius * WORLD_SCALE);
	        float screenRadius = worldRadius / worldPerPixel;

	        if (screenRadius > TRACKING_THRESHOLD_PX) continue;

	        // Body position in OpenGL space
	        float bx = (float)(b.position.x * WORLD_SCALE);
	        float by = (float)(b.position.y * WORLD_SCALE);

	        // Diagonal goes NW
	        float diagEndX = bx - diagonalLen;
	        float diagEndY = by + diagonalLen;

	        // Horizontal extends left from diagonal end
	        float horizEndX = diagEndX - horizontalLen;
	        float horizEndY = diagEndY;

	        // Draw the two line segments
	        float[] lineVerts = {
	            bx,       by,       0,   // start at body
	            diagEndX, diagEndY, 0,   // end of diagonal
	            diagEndX, diagEndY, 0,   // start of horizontal
	            horizEndX, horizEndY, 0  // end of horizontal
	        };
	        uploadAndDraw(lineVerts, GL_LINES, 0.9f, 0.9f, 0.9f, 0.7f);

	        // Render label above the horizontal line
	        renderLabel(b.getDisplayName(), horizEndX, horizEndY, worldPerPixel);
	    }
	}
	
	//Label Rendering
	private void renderLabel(String text, float worldX, float worldY, float worldPerPixel) {
		float[] screen = worldToScreen(worldX, worldY);

	    // Offset above the horizontal line end
	    float screenX = screen[0];
	    float screenY = screen[1] - 14.0f;

	    ImGui.getForegroundDrawList().addText(
	        screenX, screenY,
	        ImGui.colorConvertFloat4ToU32(0.9f, 0.9f, 0.9f, 1.0f),
	        text
	    );
	}
	
	//Render Trails
	public void renderTrails(List<Body> bodies) {
		glUseProgram(shaderProgram);

	    for (Body b : bodies) {
	        if (!b.trackable || b.trail.size() < 2) continue;

	        Vector2D[] points = b.trail.toArray(new Vector2D[0]);
	        int count = points.length;

	        // Build full vertex array
	        float[] verts = new float[count * 3];
	        for (int i = 0; i < count; i++) {
	            verts[i * 3]     = (float)(points[i].x * WORLD_SCALE);
	            verts[i * 3 + 1] = (float)(points[i].y * WORLD_SCALE);
	            verts[i * 3 + 2] = 0.0f;
	        }

	        // Draw in 5 fading segments
	        int segmentSize = Math.max(1, count / 5);
	        for (int seg = 0; seg < 5; seg++) {
	            int start = seg * segmentSize;
	            int end   = (seg == 4) ? count : Math.min((seg + 1) * segmentSize, count);
	            if (end - start < 2) continue;

	            int vertCount    = end - start;
	            float[] segVerts = new float[vertCount * 3];
	            for (int i = 0; i < vertCount; i++) {
	                int idx = (start + i) * 3;
	                if (idx + 1 >= verts.length) break;
	                segVerts[i * 3]     = verts[idx];
	                segVerts[i * 3 + 1] = verts[idx + 1];
	                segVerts[i * 3 + 2] = 0.0f;
	            }

	            float alpha = 0.1f + (seg * 0.18f);

	            // bypasses uploadAndDraw VAO state issues I was having
	            glBindVertexArray(vao);
	            glBindBuffer(GL_ARRAY_BUFFER, vbo);

	            FloatBuffer buf = MemoryUtil.memAllocFloat(segVerts.length);
	            buf.put(segVerts).flip();
	            glBufferData(GL_ARRAY_BUFFER, buf, GL_DYNAMIC_DRAW);
	            MemoryUtil.memFree(buf);

	            glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
	            glEnableVertexAttribArray(0);

	            int colorLoc = glGetUniformLocation(shaderProgram, "uColor");
	            glUniform4f(colorLoc, 0.4f, 0.7f, 1.0f, alpha);

	            glDrawArrays(GL_LINE_STRIP, 0, vertCount);
	            glBindVertexArray(0);
	        }
	    }
	}
	
	//Projected orbits for new bodies
	public void renderProjectedOrbit(Simulation sim) {
		if (!bodyFactory.isVelocitySet() && !bodyFactory.isDragging()) { return; }

		// Find the most massive body to use as the gravitational attractor
	    Body attractor = null;
	    for (Body b : sim.getBodies()) {
	        if (attractor == null || b.mass > attractor.mass) {
	            attractor = b;
	        }
	    }
	    if (attractor == null) return;
		
	    // Create a ghost Body using the existing Body class
	    double posX    = bodyFactory.getClickWorldX() / 1e-11;
	    double posY    = bodyFactory.getClickWorldY() / 1e-11;
	    Body ghost     = new Body(
	        new Vector2D(posX, posY),
	        new Vector2D(bodyFactory.getVelocityX(), bodyFactory.getVelocityY()),
	        bodyFactory.pendingMass,
	        bodyFactory.pendingRadius
	    );
	    
	    // Compute initial acceleration toward attractor
	    updateGhostAcceleration(ghost, attractor, sim.G, sim.epsilon);
	    
	    List<float[]> path = new ArrayList<>();
	    path.add(new float[]{
	        (float)(ghost.position.x * WORLD_SCALE),
	        (float)(ghost.position.y * WORLD_SCALE)
	    });
	    
	    for (int i = 0; i < ORBIT_PROJECTION_STEPS; i++) {
	        // Reuse existing halfKick and position update from Body
	        ghost.halfKick(sim.dt);
	        ghost.position = ghost.position.add(ghost.velocity.scale(sim.dt));
	        updateGhostAcceleration(ghost, attractor, sim.G, sim.epsilon);
	        ghost.halfKick(sim.dt);

	        path.add(new float[]{
	            (float)(ghost.position.x * WORLD_SCALE),
	            (float)(ghost.position.y * WORLD_SCALE)
	        });

	        if (ghost.position.magnitudeSquared() > 1e30) break;
	    }
	    
	    if (path.size() < 2) return;
	    float[] verts = new float[path.size() * 3];
	    for (int i = 0; i < path.size(); i++) {
	        verts[i * 3]     = path.get(i)[0];
	        verts[i * 3 + 1] = path.get(i)[1];
	        verts[i * 3 + 2] = 0.0f;
	    }
	    
	    //bypass uploadAndDraw.  Having issues
	    glBindVertexArray(vao);
	    glBindBuffer(GL_ARRAY_BUFFER, vbo);

	    FloatBuffer buf = MemoryUtil.memAllocFloat(verts.length);
	    buf.put(verts).flip();
	    glBufferData(GL_ARRAY_BUFFER, buf, GL_DYNAMIC_DRAW);
	    MemoryUtil.memFree(buf);

	    glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
	    glEnableVertexAttribArray(0);

	    int colorLoc = glGetUniformLocation(shaderProgram, "uColor");
	    glUniform4f(colorLoc, 0.2f, 1.0f, 0.4f, 0.4f);

	    glDrawArrays(GL_LINE_STRIP, 0, path.size());
	    glBindVertexArray(0);
	}
	
	//vector rendering
	private void renderVelocityVectors(List<Body> bodies) {
		float[] verts = new float[bodies.size() * 6];
		for (int i = 0; i < bodies.size(); i++) {
            Body b = bodies.get(i);
            float x0 = (float)(b.position.x * WORLD_SCALE);
            float y0 = (float)(b.position.y * WORLD_SCALE);
            float x1 = (float)((b.position.x + b.velocity.x * VELOCITY_SCALE) * WORLD_SCALE);
            float y1 = (float)((b.position.y + b.velocity.y * VELOCITY_SCALE) * WORLD_SCALE);
            verts[i * 6] = x0; verts[i * 6 + 1] = y0; verts[i * 6 + 2] = 0;
            verts[i * 6 + 3] = x1; verts[i * 6 + 4] = y1; verts[i * 6 + 5] = 0;
        }
        uploadAndDraw(verts, GL_LINES, 0.2f, 0.6f, 1.0f, 0.8f); // blue
        
	}
	
	private void renderAccelerationVectors(List<Body> bodies) {
        float[] verts = new float[bodies.size() * 6];
        for (int i = 0; i < bodies.size(); i++) {
            Body b   = bodies.get(i);
            float x0 = (float)(b.position.x * WORLD_SCALE);
            float y0 = (float)(b.position.y * WORLD_SCALE);
            float x1 = (float)((b.position.x + b.acceleration.x * ACCELERATION_SCALE) * WORLD_SCALE);
            float y1 = (float)((b.position.y + b.acceleration.y * ACCELERATION_SCALE) * WORLD_SCALE);
            verts[i * 6]     = x0; verts[i * 6 + 1] = y0; verts[i * 6 + 2] = 0;
            verts[i * 6 + 3] = x1; verts[i * 6 + 4] = y1; verts[i * 6 + 5] = 0;
        }
        uploadAndDraw(verts, GL_LINES, 1.0f, 0.4f, 0.2f, 0.8f); // orange
        
    }
	 
	// Quad Tree Rendering
	private void renderQuadTree(QuadTreeNode node) {
        if (node == null || node.isEmpty()) return;
        BoundingBox b  = node.boundary;
        float x0 = (float)((b.x - b.halfWidth) * WORLD_SCALE);
        float x1 = (float)((b.x + b.halfWidth) * WORLD_SCALE);
        float y0 = (float)((b.y - b.halfWidth) * WORLD_SCALE);
        float y1 = (float)((b.y + b.halfWidth) * WORLD_SCALE);
        float[] verts  = {
            x0, y0, 0,  x1, y0, 0,
            x1, y0, 0,  x1, y1, 0,
            x1, y1, 0,  x0, y1, 0,
            x0, y1, 0,  x0, y0, 0
        };
        uploadAndDraw(verts, GL_LINES, 0.2f, 0.8f, 0.2f, 0.15f); // faint green
        if (node.isInternal()) {
            for (QuadTreeNode child : node.children) {
                renderQuadTree(child);
            }
        }
        
	}
	
	//DRAWING CALLS ------------------------------------------------------------------------------------------------------------------------
	
	private void drawVelocityPreview() {
	    float bx = bodyFactory.getClickWorldX();
	    float by = bodyFactory.getClickWorldY();
	    float ex = bodyFactory.getDragEndWorldX();
	    float ey = bodyFactory.getDragEndWorldY();

	    // Only draw if drag has actually moved
	    if (bx == ex && by == ey) return;

	    // Draw preview line in world space through the MVP pipeline
	    glUseProgram(shaderProgram);
	    float[] verts = { bx, by, 0, ex, ey, 0 };
	    uploadAndDraw(verts, GL_LINES, 0.2f, 1.0f, 0.4f, 0.9f); // green

	    // Arrowhead at drag end — small cross in screen space
	    float[] cross = worldToScreen(ex, ey);
	    ImGui.getForegroundDrawList().addCircleFilled(
	        cross[0], cross[1], 4.0f,
	        ImGui.colorConvertFloat4ToU32(0.2f, 1.0f, 0.4f, 0.9f));
	}
	
	private float[] generateCircle(float cx, float cy, float radius, int segments) {
	    float[] verts = new float[segments * 9]; // segments * 3 vertices * 3 floats
	    for (int i = 0; i < segments; i++) {
	        double angle1 = 2 * Math.PI * i / segments;
	        double angle2 = 2 * Math.PI * (i + 1) / segments;
	        int idx = i * 9;

	        // Center vertex
	        verts[idx] = cx;
	        verts[idx + 1] = cy;
	        verts[idx + 2] = 0;

	        // Edge vertex 1
	        verts[idx + 3] = cx + (float)(radius * Math.cos(angle1));
	        verts[idx + 4] = cy + (float)(radius * Math.sin(angle1));
	        verts[idx + 5] = 0;

	        // Edge vertex 2
	        verts[idx + 6] = cx + (float)(radius * Math.cos(angle2));
	        verts[idx + 7] = cy + (float)(radius * Math.sin(angle2));
	        verts[idx + 8] = 0;
	    }
	    return verts;
	}
	
	//MATH / CORD CONVERSION ------------------------------------------------------------------------------------------------------------------------
	private float[] worldToScreen(float worldX, float worldY) {
		//build the same mvp the gpu uses
		Matrix4f view = camera.getViewMatrix();
		Matrix4f proj = camera.getProjectionMatrix(windowWidth, windowHeight);
		Matrix4f mvp = proj.mul(view, new Matrix4f());
		
		//Transform the point through mvp
		org.joml.Vector4f clip = mvp.transform(new org.joml.Vector4f(worldX, worldY, 0.0f, 1.0f));
		
		//prespective divide to get ndc
		float ndcX = clip.x / clip.w;
		float ndcY = clip.y / clip.w;
		
		//ndc to screen pixels
		float screenX = (ndcX + 1.0f) * 0.5f * windowWidth;
		float screenY = (1.0f - (ndcY + 1.0f) * 0.5f) * windowHeight;
		
		return new float[] { screenX, screenY };
	}
	
	private float[] screenToWorld(float screenX, float screenY) {
	    // Screen pixels to NDC
	    float ndcX =  (screenX / windowWidth)  * 2.0f - 1.0f;
	    float ndcY = -((screenY / windowHeight) * 2.0f - 1.0f);

	    // Build inverse MVP
	    Matrix4f view = camera.getViewMatrix();
	    Matrix4f proj = camera.getProjectionMatrix(windowWidth, windowHeight);
	    Matrix4f mvp  = proj.mul(view, new Matrix4f());
	    Matrix4f invMvp = mvp.invert(new Matrix4f());

	    // Transform NDC back to world space
	    org.joml.Vector4f world = invMvp.transform(
	        new org.joml.Vector4f(ndcX, ndcY, 0.0f, 1.0f));

	    return new float[]{ world.x, world.y };
	}
	
	public void updateGhostAcceleration(Body ghost, Body attractor, double G, double epsilon) {
		ghost.resetAcceleration();
		Vector2D dir = attractor.position.subtract(ghost.position);
		double dist = dir.magnitude();
		double soft = Math.sqrt(dist * dist + epsilon * epsilon);
		double mag = (G * attractor.mass) / (soft * soft * soft);
		ghost.acceleration = dir.scale(mag);
	}
	
	 
	//GL HELPERS ------------------------------------------------------------------------------------------------------------------------
	 
	private void uploadAndDraw(float[] verts, int mode, float r, float g, float b, float a) {
			glBindVertexArray(vao);
			glBindBuffer(GL_ARRAY_BUFFER, vbo);
			
			FloatBuffer buf = MemoryUtil.memAllocFloat(verts.length);
			buf.put(verts).flip();
			glBufferData(GL_ARRAY_BUFFER, buf, GL_DYNAMIC_DRAW);
			MemoryUtil.memFree(buf);
			
			glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
		    glEnableVertexAttribArray(0);
			
			int colorLoc = glGetUniformLocation(shaderProgram, "uColor");
			glUniform4f(colorLoc, r, g, b, a);
			
			glDrawArrays(mode, 0, verts.length / 3);
			glBindVertexArray(0);
	}
			
	private void setUniformMatrix(Matrix4f mat) {
		FloatBuffer buf = MemoryUtil.memAllocFloat(16);
		mat.get(buf);
		int loc = glGetUniformLocation(shaderProgram, "uMVP");
		glUniformMatrix4fv(loc, false, buf);
		MemoryUtil.memFree(buf);
	}	
		
	
	//SHADERS ------------------------------------------------------------------------------------------------------------------------
	private void setupShaders() {
		String vertSrc =
		    "#version 330 core\n" +
		    "layout(location = 0) in vec3 aPos;\n" +
		    "uniform mat4 uMVP;\n" +
		    "void main() {\n" +
		    "    gl_Position = uMVP * vec4(aPos, 1.0);\n" +
		    "}\n";

        String fragSrc =
            "#version 330 core\n" +
            "uniform vec4 uColor;\n" +
            "out vec4 FragColor;\n" +
            "void main() {\n" +
            "    FragColor = uColor;\n" +
            "}\n";

        int vert = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vert, vertSrc);
        glCompileShader(vert);
        checkShader(vert, "vertex");

        int frag = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(frag, fragSrc);
        glCompileShader(frag);
        checkShader(frag, "fragment");

        shaderProgram = glCreateProgram();
        glAttachShader(shaderProgram, vert);
        glAttachShader(shaderProgram, frag);
        glLinkProgram(shaderProgram);

        glDeleteShader(vert);
        glDeleteShader(frag);
        
    }
	
	private void setupBackground() {
		// Full screen quad — two triangles covering NDC space
	    float[] verts = {
	        -1f, -1f, 0f,  0f, 0f,
	         1f, -1f, 0f,  1f, 0f,
	         1f,  1f, 0f,  1f, 1f,
	        -1f, -1f, 0f,  0f, 0f,
	         1f,  1f, 0f,  1f, 1f,
	        -1f,  1f, 0f,  0f, 1f,
	    };
	    
	    backgroundVao = glGenVertexArrays();
	    backgroundVbo = glGenBuffers();
	    
	    glBindVertexArray(backgroundVao);
	    glBindBuffer(GL_ARRAY_BUFFER, backgroundVbo);
	    
	    FloatBuffer buf = MemoryUtil.memAllocFloat(verts.length);
	    buf.put(verts).flip();
	    glBufferData(GL_ARRAY_BUFFER, buf, GL_STATIC_DRAW);
	    MemoryUtil.memFree(buf);
	    
	    // position attribute: location 0, 3 floats, stride 5 floats
	    glVertexAttribPointer(0, 3, GL_FLOAT, false, 5 * Float.BYTES, 0);
	    glEnableVertexAttribArray(0);
	    
	    // uv attribute: location 1, 2 floats, stride 5 floats, offset 3
	    glVertexAttribPointer(1, 2, GL_FLOAT, false,
	        5 * Float.BYTES, 3 * Float.BYTES);
	    glEnableVertexAttribArray(1);

	    glBindVertexArray(0);
	    
	    // Background shader
	    String vertSrc =
	        "#version 330 core\n" +
	        "layout(location = 0) in vec3 aPos;\n" +
	        "layout(location = 1) in vec2 aUV;\n" +
	        "out vec2 vUV;\n" +
	        "void main() {\n" +
	        "    gl_Position = vec4(aPos, 1.0);\n" +
	        "    vUV = aUV;\n" +
	        "}\n";

	    String fragSrc =
	        "#version 330 core\n" +
	        "in vec2 vUV;\n" +
	        "uniform sampler2D uTexture;\n" +
	        "out vec4 FragColor;\n" +
	        "void main() {\n" +
	        "    FragColor = texture(uTexture, vUV);\n" +
	        "}\n";
	    
	    int vert = glCreateShader(GL_VERTEX_SHADER);
	    glShaderSource(vert, vertSrc);
	    glCompileShader(vert);
	    checkShader(vert, "background vertex");
	    
	    int frag = glCreateShader(GL_FRAGMENT_SHADER);
	    glShaderSource(frag, fragSrc);
	    glCompileShader(frag);
	    checkShader(frag, "background fragment");
	    
	    backgroundShader = glCreateProgram();
	    glAttachShader(backgroundShader, vert);
	    glAttachShader(backgroundShader, frag);
	    glLinkProgram(backgroundShader);
	    
	    glDeleteShader(vert);
	    glDeleteShader(frag);
	    
	}

    private void checkShader(int shader, String name) {
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            throw new RuntimeException(name + " shader error: "
                + glGetShaderInfoLog(shader));
        }
        
    }

    //BUFFERS ------------------------------------------------------------------------------------------------------------------------
    private void setupBuffers() {
    	vao = glGenVertexArrays();
        vbo = glGenBuffers();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
        glEnableVertexAttribArray(0);
        glBindVertexArray(0);
    }
    
    //INPUT CALLBACKS ------------------------------------------------------------------------------------------------------------------------
    private void setupCallbacks(Simulation sim) {
        glfwSetFramebufferSizeCallback(windowHandle, (win, w, h) -> {
            windowWidth  = w;
            windowHeight = h;
            glViewport(0, 0, w, h);
        });

        glfwSetScrollCallback(windowHandle, (win, dx, dy) -> {
        	
        	//Dont zoom in and out if youre over a gui panel
        	if (!ImGui.isWindowHovered(imgui.flag.ImGuiHoveredFlags.AnyWindow)) {
	            // Get mouse position in screen pixels
	            double[] mx = new double[1], my = new double[1];
	            glfwGetCursorPos(win, mx, my);
	
	            // Convert mouse screen position to world space BEFORE zooming
	            float worldBeforeX = screenToWorld((float)mx[0], (float)my[0])[0];
	            float worldBeforeY = screenToWorld((float)mx[0], (float)my[0])[1];
	
	            // Apply zoom
	            camera.zoom(dy > 0 ? 1.1f : 0.9f);
	
	            // Convert same screen position to world space AFTER zooming
	            float worldAfterX = screenToWorld((float)mx[0], (float)my[0])[0];
	            float worldAfterY = screenToWorld((float)mx[0], (float)my[0])[1];
	
	            // Pan camera by the difference to keep the point under the mouse fixed
	            camera.x -= (worldAfterX - worldBeforeX);
	            camera.y -= (worldAfterY - worldBeforeY);
        	}
        });

        glfwSetMouseButtonCallback(windowHandle, (win, button, action, mods) -> {
        	//right click - camera pan
            if (button == GLFW_MOUSE_BUTTON_RIGHT) {
            	//Only pan in the simluation area, not the UI
            	if (action == GLFW_PRESS && !ImGui.isWindowHovered(imgui.flag.ImGuiHoveredFlags.AnyWindow)) {
            		mouseDown = true;
            	} else if (action == GLFW_RELEASE) {
            		mouseDown = false;
            	}
	                double[] mx = new double[1], my = new double[1];
	                glfwGetCursorPos(win, mx, my);
	                lastMouseX = mx[0];
	                lastMouseY = my[0];
            }
            
            //left click - body creation state machine
            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                double[] mx = new double[1], my = new double[1];
                glfwGetCursorPos(win, mx, my);
                float screenX = (float)mx[0];
                float screenY = (float)my[0];

                if (action == GLFW_PRESS) {
                    // Don't interact with simulation if over UI
                    if (ImGui.isWindowHovered(imgui.flag.ImGuiHoveredFlags.AnyWindow)) { return; }
                    
                    if (justCommittedBody) {
                        justCommittedBody = false;
                        return;
                    }
                    
                    if (bodyFactory.isIdle()) {
//                    	System.out.println("  -> triggering onWorldClick");
                        float[] world = screenToWorld(screenX, screenY);
                        bodyFactory.onWorldClick(world[0], world[1], screenX, screenY);

                    } else if (bodyFactory.isConfiguring()) {
//                    	 System.out.println("  -> triggering startDrag");
                        // Start drag for velocity
                        float[] world = screenToWorld(screenX, screenY);
                        bodyFactory.startDrag(world[0], world[1]);
                    } else {
//                    	 System.out.println("  -> no handler for state: " + bodyFactory.getState());
                    }
                }

                if (action == GLFW_RELEASE) {
//                	System.out.println("LEFT RELEASE - state: " + bodyFactory.getState());
                    if (bodyFactory.isDragging()) {
//                    	System.out.println("  -> triggering endDrag");
                        bodyFactory.endDrag();
                    }
                }
            }
        });

        glfwSetCursorPosCallback(windowHandle, (win, mx, my) -> {
            // Right click pan
            if (mouseDown && !ImGui.isWindowHovered(
                    imgui.flag.ImGuiHoveredFlags.AnyWindow)) {
                float dx = (float)(mx - lastMouseX);
                float dy = (float)(my - lastMouseY);
                float viewSize      = 20.0f / camera.zoom;
                float pixelsToWorld = (viewSize * 2) / windowWidth;
                camera.pan(-dx * pixelsToWorld, dy * pixelsToWorld);
            }
            lastMouseX = mx;
            lastMouseY = my;

            // Left drag — update velocity preview
            if (bodyFactory.isDragging()) {
                float[] world = screenToWorld((float)mx, (float)my);
                bodyFactory.updateDrag(world[0], world[1]);
            }
        });

        glfwSetKeyCallback(windowHandle, (win, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
                glfwSetWindowShouldClose(win, true);
            }
//            if (key == GLFW_KEY_V && action == GLFW_PRESS) {
//                showVelocityVectors = !showVelocityVectors;
//            }
//            if (key == GLFW_KEY_A && action == GLFW_PRESS) {
//                showAccelerationVectors = !showAccelerationVectors;
//            }
//            if (key == GLFW_KEY_Q && action == GLFW_PRESS) {
//                showQuadTree = !showQuadTree;
//            }
//            if (key == GLFW_KEY_T && action == GLFW_PRESS) {
//                showTrails = !showTrails;
//            }
            if (key == GLFW_KEY_SPACE && action == GLFW_PRESS) {
                sim.togglePause();
            }
            
        });
    }
    
    // Window State
    public boolean shouldClose() {
        return glfwWindowShouldClose(windowHandle);
    }

    public void cleanup() {
    	imguiGl3.dispose();
    	imguiGlfw.dispose();
    	ImGui.destroyContext();
    	
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vbo);
        glDeleteProgram(shaderProgram);
        glfwDestroyWindow(windowHandle);
        glfwTerminate();
    }
        
    //Background Texture
    private int loadTexture(String path) {
    	int[] width = new int[1];
    	int[] height =new int[1];
    	int[] channels = new int[1];
    	
    	org.lwjgl.stb.STBImage.stbi_set_flip_vertically_on_load(true);
    	java.nio.ByteBuffer image = org.lwjgl.stb.STBImage.stbi_load(
    			path, width, height, channels, 4);
    	
    	if (image == null) {
    		throw new RuntimeException("Failed to load texture: " + path + " -- " + org.lwjgl.stb.STBImage.stbi_failure_reason());
    	}
    	
    	int textureId = glGenTextures();
    	glBindTexture(GL_TEXTURE_2D, textureId);
    	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
    	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width[0], height[0], 0, GL_RGBA, GL_UNSIGNED_BYTE, image);
        
        org.lwjgl.stb.STBImage.stbi_image_free(image);
        return textureId;
    }    
    
    //File Explorerer ------------------------------------------------------------------------------------------------------------------------

    private java.io.File openFileChooser(boolean saveMode) {
    	
    	// Ensure saves directory exists
        java.io.File savesDir = new java.io.File("saves");
        if (!savesDir.exists()) {
            savesDir.mkdirs();
        }
    	
        String path;
        try (MemoryStack stack = MemoryStack.stackPush()) {
	        PointerBuffer filters = stack.mallocPointer(1);
	        filters.put(stack.UTF8("*.json"));
	        filters.flip();
	        
	        if (saveMode) {
	            path = TinyFileDialogs.tinyfd_saveFileDialog(
	                "Save Simulation",
	                "saves/simulation.json",
	                filters,
	                "Simulation Files (*.json)"
	            );
	        } else {
	            path = TinyFileDialogs.tinyfd_openFileDialog(
	                "Load Simulation",
	                "saves/simulation.json",
	                filters,
	                "Simulation Files (*.json)",
	                false
	            );
	        }
        }
        
        if (path == null) {
            return null;
        }
 
        return new File(path);
    }
}













