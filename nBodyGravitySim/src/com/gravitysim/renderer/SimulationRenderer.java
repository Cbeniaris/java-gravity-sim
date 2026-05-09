package com.gravitysim.renderer;

import com.gravitysim.core.Body;
import com.gravitysim.core.Simulation;
import com.gravitysim.tree.*;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.List;

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
	
	//init
	
	public void init() {
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
		
		setupCallbacks();
		setupShaders();
		setupBuffers();
		
		glEnable(GL_BLEND);
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
	}
	
	//main render call
	
	public void render(Simulation sim) {
		glClearColor(0.05f, 0.05f, 0.08f, 1.0f);
		glClear(GL_COLOR_BUFFER_BIT);
		Matrix4f view = camera.getViewMatrix();
		Matrix4f proj = camera.getProjectionMatrix(windowWidth, windowHeight);
		Matrix4f mvp = proj.mul(view, new Matrix4f());
		
		glUseProgram(shaderProgram);
		setUniformMatrix(mvp);
		
		renderBodies(sim.getBodies());
		
		if (showVelocityVectors) { renderVelocityVectors(sim.getBodies());}
		if (showAccelerationVectors) renderAccelerationVectors(sim.getBodies());
        if (showQuadTree)            renderQuadTree(sim.getTree().getRoot());
        
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
	        float cx     = (float)(b.position.x * WORLD_SCALE);
	        float cy     = (float)(b.position.y * WORLD_SCALE);
	        float radius = (float)(b.radius * WORLD_SCALE);

	        // How many world units correspond to 1 pixel at current zoom
	        float viewSize = 20.0f / camera.zoom;
	        float worldUnitsPerPixel = (viewSize * 2) / windowWidth;
	        float minRadius = 1f * worldUnitsPerPixel; // minimum 1.5 pixels on scree
	        
	        radius = Math.max(radius, minRadius);
	        
	        float[] circle = generateCircle(cx, cy, radius, segments);
	        System.arraycopy(circle, 0, verts, offset, circle.length);
	        offset += circle.length;
	    }

	    uploadAndDraw(verts, GL_TRIANGLES, 1.0f, 1.0f, 1.0f, 1.0f);
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
	
	//vector rednering
	
	private void renderVelocityVectors(List<Body> bodies) {
		float[] verts = new float[bodies.size() * 6];
		for (int i = 0; i < bodies.size(); i++) {
            Body b   = bodies.get(i);
            float x0 = (float)(b.position.x * WORLD_SCALE);
            float y0 = (float)(b.position.y * WORLD_SCALE);
            float x1 = (float)((b.position.x + b.velocity.x * VELOCITY_SCALE) * WORLD_SCALE);
            float y1 = (float)((b.position.y + b.velocity.y * VELOCITY_SCALE) * WORLD_SCALE);
            verts[i * 6]     = x0; verts[i * 6 + 1] = y0; verts[i * 6 + 2] = 0;
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
	        float x0       = (float)((b.x - b.halfWidth) * WORLD_SCALE);
	        float x1       = (float)((b.x + b.halfWidth) * WORLD_SCALE);
	        float y0       = (float)((b.y - b.halfWidth) * WORLD_SCALE);
	        float y1       = (float)((b.y + b.halfWidth) * WORLD_SCALE);
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
	 
	 //GL Helpers
	 
	 private void uploadAndDraw(float[] verts, int mode, float r, float g, float b, float a) {
			glBindVertexArray(vao);
			glBindBuffer(GL_ARRAY_BUFFER, vbo);
			
			FloatBuffer buf = MemoryUtil.memAllocFloat(verts.length);
			buf.put(verts).flip();
			glBufferData(GL_ARRAY_BUFFER, buf, GL_DYNAMIC_DRAW);
			MemoryUtil.memFree(buf);
			
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
			
	// shaders
		
		
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

	    private void checkShader(int shader, String name) {
	        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
	            throw new RuntimeException(name + " shader error: "
	                + glGetShaderInfoLog(shader));
	        }
	    }

	    // buffers
	    private void setupBuffers() {
	    	vao = glGenVertexArrays();
	        vbo = glGenBuffers();

	        glBindVertexArray(vao);
	        glBindBuffer(GL_ARRAY_BUFFER, vbo);
	        glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
	        glEnableVertexAttribArray(0);
	        glBindVertexArray(0);
	    }
	    
	    //input callbacks
	    
	    private void setupCallbacks() {
	        glfwSetFramebufferSizeCallback(windowHandle, (win, w, h) -> {
	            windowWidth  = w;
	            windowHeight = h;
	            glViewport(0, 0, w, h);
	        });

	        glfwSetScrollCallback(windowHandle, (win, dx, dy) -> {
	            camera.zoom(dy > 0 ? 1.1f : 0.9f);
	        });

	        glfwSetMouseButtonCallback(windowHandle, (win, button, action, mods) -> {
	            if (button == GLFW_MOUSE_BUTTON_LEFT) {
	                mouseDown = action == GLFW_PRESS;
	                double[] mx = new double[1], my = new double[1];
	                glfwGetCursorPos(win, mx, my);
	                lastMouseX = mx[0];
	                lastMouseY = my[0];
	            }
	        });

	        glfwSetCursorPosCallback(windowHandle, (win, mx, my) -> {
	            if (mouseDown) {
	            	 float dx = (float)(mx - lastMouseX);
	                 float dy = (float)(my - lastMouseY);

	                 // Negate dx and dy so dragging right moves the world right
	                 // (camera moves opposite to finger — tablet/map behaviour)
	                 // viewSize controls how much world space one pixel represents
	                 float viewSize = 20.0f / camera.zoom;
	                 float pixelsToWorld = (viewSize * 2) / windowWidth;

	                 camera.pan(-dx * pixelsToWorld, dy * pixelsToWorld);

	                 lastMouseX = mx;
	                 lastMouseY = my;
	            }
	        });

	        glfwSetKeyCallback(windowHandle, (win, key, scancode, action, mods) -> {
	            if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
	                glfwSetWindowShouldClose(win, true);
	            }
	            if (key == GLFW_KEY_V && action == GLFW_PRESS) {
	                showVelocityVectors = !showVelocityVectors;
	            }
	            if (key == GLFW_KEY_A && action == GLFW_PRESS) {
	                showAccelerationVectors = !showAccelerationVectors;
	            }
	            if (key == GLFW_KEY_Q && action == GLFW_PRESS) {
	                showQuadTree = !showQuadTree;
	            }
	        });
	    }
	    
	    // Window State
	    
	    public boolean shouldClose() {
	        return glfwWindowShouldClose(windowHandle);
	    }

	    public void cleanup() {
	        glDeleteVertexArrays(vao);
	        glDeleteBuffers(vbo);
	        glDeleteProgram(shaderProgram);
	        glfwDestroyWindow(windowHandle);
	        glfwTerminate();
	    }
}













