package com.example.demo.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {


@CrossOrigin(origins = "*")

       @GetMapping("/")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "service", "ZenTime Backend");
    }

    @GetMapping("/health")
    public Map<String, Object> healthAlias() {
        return Map.of(
                "status", "ok",
                "service", "ZenTime Backend");
    }

    @CrossOrigin(origins = "*")
       @GetMapping("/hello")
    public String sayHello() {
        return "Hello World from Coimbatore!";
    }
}
