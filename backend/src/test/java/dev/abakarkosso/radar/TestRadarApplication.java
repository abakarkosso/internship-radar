package dev.abakarkosso.radar;

import org.springframework.boot.SpringApplication;

public class TestRadarApplication {

	public static void main(String[] args) {
		SpringApplication.from(RadarApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
