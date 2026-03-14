package org.GLM.negoriator.controller;

import org.springframework.stereotype.Controller;

import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/")
public class HelloController {

    public HelloController() {
    }

    @Override
    public String toString() {
        return "HelloController";
    }
}
