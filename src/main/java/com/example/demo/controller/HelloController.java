package com.example.demo.controller;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {


@CrossOrigin(origins = "*")

       @GetMapping("/hello")
    public String sayHello() {
        return "Hello World from Coimbatore!";
    }
}
