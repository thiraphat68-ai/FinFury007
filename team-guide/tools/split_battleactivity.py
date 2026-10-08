#!/usr/bin/env python3
"""แยก _full_annotated/BattleActivity.java ออกเป็นไฟล์ของคนที่ 1 และคนที่ 3
ตามป้าย //@@P1 ... //@@END และ //@@P3 ... //@@END ในไฟล์เต็ม
วิธีใช้:  python3 tools/split_battleactivity.py   (รันที่โฟลเดอร์ team-guide)"""
import re
src = open("_full_annotated/BattleActivity.java", encoding="utf-8").read().split("\n")
first = next(i for i, l in enumerate(src) if l.strip().startswith("//@@P"))
header = src[:first]
secs = {"1": [], "3": []}
cur = None
for l in src[first:]:
    m = re.match(r"\s*//@@P(\d)", l)
    if m: cur = m.group(1); secs[cur].append([]); continue
    if l.strip() == "//@@END": cur = None; continue
    if cur: secs[cur][-1].append(l)
def build(p, title, out):
    body = []
    for n, s in enumerate(secs[p], 1):
        body.append(f"    // ------------------ ส่วนที่ {n} ของคนที่ {p} (ในไฟล์เต็ม) ------------------")
        body += s
    note = [
        f"// *** ไฟล์ตัวอย่างเฉพาะส่วนของ {title} ***",
        "// ตัดส่วนของคนอื่นออกเพื่อให้อ่านง่าย ไฟล์นี้ใช้ build ไม่ได้ ให้ดูโค้ดเต็มที่ _full_annotated/BattleActivity.java",
        "// ตำแหน่งไฟล์จริง: app/src/main/java/com/example/finfury/BattleActivity.java",
        ""]
    open(out, "w", encoding="utf-8").write("\n".join(note + header + [""] + body + ["}", ""]))
build("1", "คนที่ 1 (Controller: กติกา / game loop / ชนะ-แพ้)", "person1_controller/BattleActivity_P1_rules.java")
build("3", "คนที่ 3 (View: HUD / overlay / หน้าผลลัพธ์)", "person3_view/BattleActivity_P3_hud.java")
print("แยกเรียบร้อย")
