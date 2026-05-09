package com.gravitysim.core;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class SimulationIO {
	private static final Gson gson = new GsonBuilder()
			.setPrettyPrinting()
			.create();
	
	//Internal DTO
	private static class BodyDTO {
		double posX, posY;
		double velX, velY;
		double mass;
		double radius;
		
		BodyDTO(Body b) {
			posX = b.position.x;
			posY = b.position.y;
			velX = b.velocity.x;
			velY = b.velocity.y;
			mass = b.mass;
			radius = b.radius;
		}
		
		Body toBody() {
			return new Body(
				new Vector2D(posX, posY),
				new Vector2D(velX, velY),
				mass, radius); 
		}
	}
	
	private static class SimulationDTO {
		double G;
		double epsilon;
		double dt;
		List<BodyDTO> bodies;
	}
	
	//Saving
	public static void save(Simulation sim, String filePath) throws IOException {
		SimulationDTO dto = new SimulationDTO();
		dto.G = sim.G;
		dto.epsilon = sim.epsilon;
		dto.dt = sim.dt;
		dto.bodies = new ArrayList<>();
		
		for (Body b : sim.getBodies()) {
			dto.bodies.add(new BodyDTO(b));
		}
	
		try (Writer writer = new FileWriter(filePath)) {
			gson.toJson(dto, writer);
		}
	}
	
	//loading
	public static void load(Simulation sim, String filePath) throws IOException {
		try (Reader reader = new FileReader(filePath)) {
			SimulationDTO dto = gson.fromJson(reader, SimulationDTO.class); 
			
			sim.G = dto.G;
			sim.epsilon = dto.epsilon;
			sim.dt = dto.dt;
			
			sim.getBodies().clear();
			for (BodyDTO b : dto.bodies) {
				sim.addBody(b.toBody());
			}
		
			sim.initialize();
		}
	}
	
}
