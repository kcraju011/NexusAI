package com.nexusai.core;

public class Service {
    private final String id;
    private final String name;

    public Service(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}