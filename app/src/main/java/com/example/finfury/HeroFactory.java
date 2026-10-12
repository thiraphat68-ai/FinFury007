package com.example.finfury;

public class HeroFactory {
    /** สร้างฮีโร่ตามรหัส (1 = Swordfish, 2 = Pufferfish, 3 = Shark, 4 = Octopus, 5 = Electric Eel) */
    public static Hero createHero(int heroId) {
        switch (heroId) {
            case 2:
                return new Pufferfish();
            case 3:
                return new Shark();
            case 4:
                return new Octopus();
            case 5:
                return new ElectricEel();
            case 1:
            default:
                return new Swordfish();
        }
    }
}