package com.ali.carsystem;

public final class VideoItem {
    public final String id;
    public final String title;
    public final String author;
    public final String thumbnail;
    public final String backend;
    public final String baseUrl;

    public VideoItem(String id, String title, String author, String thumbnail,
                     String backend, String baseUrl) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.thumbnail = thumbnail;
        this.backend = backend;
        this.baseUrl = baseUrl;
    }
}
