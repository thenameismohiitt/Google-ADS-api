package com.nexthoughts.googleads.controller;

import com.nexthoughts.googleads.service.GoogleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/google/api")
@CrossOrigin(origins = "*")
public class Google {

    @Autowired
    public GoogleService googleService;

    @GetMapping("/refresh/token")
    public String getRefreshToken(){
        return googleService.getRefreshToken();
    }

    @GetMapping("/getStatsOfAds")
    public ResponseEntity<?> getStatsOfAd(){
        return googleService.getStats();
    }
//
//    @GetMapping("/create/campaign")
//    public ResponseEntity<?> createCampaign(){
//        googleService.addCampaign();
//        return new ResponseEntity<>("Campaign created!",HttpStatus.OK);
//    }
}
