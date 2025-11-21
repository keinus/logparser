package org.keinus.logparser.controller;

import org.keinus.logparser.service.ConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;

import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final ConfigService configService;

    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    @GetMapping
    public Map<String, Object> getConfig() throws FileNotFoundException {
        return configService.getConfig();
    }

    @PostMapping("/{section}")
    public Map<String, Object> addConfig(@PathVariable String section, @RequestBody Map<String, Object> newConfig) throws IOException {
        return configService.addConfig(section, newConfig);
    }

    @PutMapping("/{section}/{index}")
    public Map<String, Object> updateConfig(@PathVariable String section, @PathVariable int index, @RequestBody Map<String, Object> newConfig) throws IOException {
        return configService.updateConfig(section, index, newConfig);
    }

    @DeleteMapping("/{section}/{index}")
    public Map<String, Object> deleteConfig(@PathVariable String section, @PathVariable int index) throws IOException {
        return configService.deleteConfig(section, index);
    }

    @PutMapping("/common")
    public Map<String, Object> updateCommonConfig(@RequestBody Map<String, Object> commonConfig) throws IOException {
        return configService.updateCommonConfig(commonConfig);
    }
}
