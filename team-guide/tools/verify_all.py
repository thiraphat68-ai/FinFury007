#!/usr/bin/env python3
"""ตรวจรวม: ทุกไฟล์ในโฟลเดอร์คู่มือ (ที่เป็นสำเนาเต็ม) ต้องมี "โค้ดเหมือนต้นฉบับทุกตัวอักษร"
วิธีใช้ (รันที่โฟลเดอร์ team-guide):  python3 tools/verify_all.py
ข้ามไฟล์ที่เป็นแค่ส่วนย่อย (BattleActivity_P1_rules / BattleActivity_P3_hud) เพราะตัดบางส่วนออก"""
import os, sys, subprocess
HERE = os.path.dirname(os.path.abspath(__file__))
GUIDE = os.path.dirname(HERE)
SRC = os.path.join(GUIDE, "..", "app", "src", "main")
orig = {}
for root, _, files in os.walk(os.path.join(SRC, "java")):
    for f in files: orig[f] = os.path.join(root, f)
for f in os.listdir(os.path.join(SRC, "res", "layout")):
    orig[f] = os.path.join(SRC, "res", "layout", f)
SKIP = {"BattleActivity_P1_rules.java", "BattleActivity_P3_hud.java"}
bad = n = 0
seen = set()
for d in sorted(os.listdir(GUIDE)):
    p = os.path.join(GUIDE, d)
    if not (os.path.isdir(p) and (d.startswith("person") or d.startswith("shared"))): continue
    for f in sorted(os.listdir(p)):
        if f in SKIP: continue
        if f not in orig: print("ไม่พบไฟล์ต้นฉบับของ", d, f); bad += 1; continue
        r = subprocess.run([sys.executable, os.path.join(HERE, "verify_same_code.py"), orig[f], os.path.join(p, f)],
                           capture_output=True, text=True)
        n += 1; seen.add(f)
        print(r.stdout.strip())
        if r.returncode: bad += 1
missing = sorted(set(k for k in orig if k.endswith((".java", ".xml")) and k not in
                     ("ExampleInstrumentedTest.java", "ExampleUnitTest.java", "AndroidManifest.xml")) - seen)
print(f"\nตรวจแล้ว {n} ไฟล์ ผิดพลาด {bad} ไฟล์")
if missing: print("ไฟล์ต้นฉบับที่ยังไม่มีสำเนาคู่มือ:", ", ".join(missing))
sys.exit(1 if bad else 0)
