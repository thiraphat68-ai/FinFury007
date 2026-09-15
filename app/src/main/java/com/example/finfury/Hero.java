package com.example.finfury;

public abstract class Hero {
    protected String name;
    protected String subject;

    public Hero(String name, String subject) {
        this.name = name;
        this.subject = subject;
    }

    public String getName() { return name; }
    public String getSubject() { return subject; }

    public abstract void executeUltimateSkill();
}