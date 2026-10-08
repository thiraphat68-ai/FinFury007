// =====================================================================================
// [คนที่ 2 - Model ฮีโร่และสกิล]  ไฟล์: HeroFactory.java  (18 บรรทัด)
// ตำแหน่งไฟล์จริง: app/src/main/java/com/example/finfury/HeroFactory.java
// สำเนานี้เพิ่มคอมเมนต์ โค้ดเหมือนไฟล์จริงทุกตัวอักษร
//
// [ไฟล์นี้คืออะไร]
//   "โรงงานสร้างฮีโร่" (Factory pattern): รับเลขฮีโร่ (1..5) แล้วคืน object ฮีโร่ตัวนั้น
//   รวมการสร้างไว้ที่เดียว ผู้เรียกไม่ต้องรู้ว่ามีคลาสอะไรบ้าง
//   ผู้เรียก: BattleActivity.onCreate (สร้างฮีโร่ที่เลือก) และ MainActivity.selectHero (ดึงชื่อ/คำอธิบายสกิลไปโชว์)
//
// [เลขฮีโร่] 1 = Swordfish , 2 = Pufferfish , 3 = Shark , 4 = Octopus , 5 = ElectricEel
//
// [จะ "เพิ่มฮีโร่" ยังไง] เพิ่ม case ใหม่ก่อน case 1:  เช่น  case 6: return new Dolphin();
// [จะ "แก้" ยังไง]  สลับเลขฮีโร่ = สลับ case (แต่ต้องแก้ใน MainActivity / BattleActivity ให้ตรงกันด้วย)
// [หมายเหตุ] case 1 กับ default อยู่ด้วยกัน = ถ้าเลขแปลก ๆ จะได้ Swordfish เป็นค่าเริ่มต้น
// =====================================================================================
package com.example.finfury;

public class HeroFactory {
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
