package com.anish.ib.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ib.reddit")
public class RedditProperties {
    private String clientId = "";
    private String clientSecret = "";
    private String userAgent = "interview-bank/1.0";
    private String subreddits = "developersIndia,leetcode,cscareerquestions";

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public String getSubreddits() { return subreddits; }
    public void setSubreddits(String subreddits) { this.subreddits = subreddits; }
}
