package com.insurance.policy.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DummyController {
    @GetMapping("/_test")
    public String test() {
        return "ok";
    }
}
