    package com.example.demo;

    import java.util.TimeZone;

    import org.springframework.boot.SpringApplication;
    import org.springframework.boot.autoconfigure.SpringBootApplication;
    import org.springframework.boot.CommandLineRunner;
    import org.springframework.context.annotation.Bean;
    import org.springframework.scheduling.annotation.EnableScheduling;
    
    import com.example.demo.service.SchemaMaintenanceService;

    import jakarta.annotation.PostConstruct;
    import jakarta.servlet.http.HttpServlet;
    @SpringBootApplication
    @EnableScheduling
    public class DemoApplication extends HttpServlet{

        public static void main(String[] args) {
            SpringApplication.run(DemoApplication.class, args);
            System.out.println("Hello World from Coimbatore!");

    }

    @PostConstruct
        public void init() {    
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata")); // Or UTC
            System.out.println("Default timezone set to Asia/Kolkata");
        }

        @Bean
        CommandLineRunner ensureSchemaOnStartup(SchemaMaintenanceService schemaMaintenanceService) {
            return args -> schemaMaintenanceService.ensureEmployeeSchema();
        }

        }
    

