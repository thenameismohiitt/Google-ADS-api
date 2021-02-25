package com.nexthoughts.googleads.payload;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class GoogleResponse {

    private String adId;
    private Long impressions;
    private Long clicks;
    private Long avgCpm;
    private String campaignId;
    private String campaignName;
}
