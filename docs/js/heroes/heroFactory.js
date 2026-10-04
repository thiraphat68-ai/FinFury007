// Port of HeroFactory.java: hero id -> hero. 1 Swordfish, 2 Pufferfish, 3 Shark, 4 Octopus, 5 Electric Eel.
import Swordfish from './Swordfish.js';
import Pufferfish from './Pufferfish.js';
import Shark from './Shark.js';
import Octopus from './Octopus.js';
import ElectricEel from './ElectricEel.js';

export const HERO_IDS = [1, 2, 3, 4, 5];

export function createHero(heroId) {
  switch (heroId) {
    case 2: return new Pufferfish();
    case 3: return new Shark();
    case 4: return new Octopus();
    case 5: return new ElectricEel();
    case 1:
    default: return new Swordfish();
  }
}
