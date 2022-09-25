package com.example.nytimesrss.controllers;

import com.example.nytimesrss.model.RssFeed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.nytimesrss.services.RssRequestService;
import org.springframework.web.bind.annotation.RestController;

import static com.example.nytimesrss.config.Constants.FRONTEND_ORIGIN;

@RestController
public class RssRequestController {

    @Autowired
    RssRequestService requestService;

    @GetMapping("/technology-feed")
    @CrossOrigin(FRONTEND_ORIGIN)
    public ResponseEntity<RssFeed> helloWorldController() {
        return ResponseEntity.ok(requestService.getRssFeed());
    }
    
}
