#!/usr/bin/env python3
"""แทรกคอมเมนต์ลงในไฟล์ต้นฉบับ โดยไม่แตะโค้ด
วิธีใช้:  python3 annotate.py <ไฟล์ต้นฉบับ> <ไฟล์บันทึก .notes> <ไฟล์ผลลัพธ์>

รูปแบบไฟล์บันทึก (.notes):
    @@ <เลขบรรทัดในต้นฉบับ> | <ข้อความบางส่วนของบรรทัดนั้นไว้ตรวจว่าไม่ผิดบรรทัด>
    ข้อความคอมเมนต์ บรรทัดที่ 1
    ข้อความคอมเมนต์ บรรทัดที่ 2
    (บรรทัดว่างในบันทึก = บรรทัดคอมเมนต์ว่าง)
คอมเมนต์จะถูกแทรก "ก่อน" บรรทัดที่ระบุ ใช้การย่อหน้าเดียวกับบรรทัดนั้น
Java ใช้ //   ,  XML ใช้ <!-- -->
เสร็จแล้วตรวจอัตโนมัติว่าโค้ดเหมือนต้นฉบับทุกตัวอักษร (เรียก verify_same_code.py)"""
import sys, re, os, subprocess

def parse(notes_path):
    items, cur = [], None
    for raw in open(notes_path, encoding="utf-8").read().split("\n"):
        m = re.match(r"@@\s*(\d+)\s*\|\s*(.*)$", raw)
        if m:
            cur = {"line": int(m.group(1)), "snip": m.group(2).strip(), "text": []}
            items.append(cur)
        elif cur is not None:
            cur["text"].append(raw)
    for it in items:
        while it["text"] and it["text"][-1].strip() == "":
            it["text"].pop()
    return items

def main(src, notes, out):
    lines = open(src, encoding="utf-8").read().split("\n")
    is_xml = src.endswith(".xml")
    by_line = {}
    for it in parse(notes):
        n = it["line"]
        if not (1 <= n <= len(lines)):
            sys.exit(f"ERROR {notes}: บรรทัด {n} เกินไฟล์ ({len(lines)})")
        window = "\n".join(lines[n - 1:n + 2])   # บรรทัดที่ระบุ + อีก 2 บรรทัดถัดไป (เผื่อเส้นแบ่ง // ====)
        if it["snip"] and it["snip"] not in window:
            sys.exit(f"ERROR {notes}: บรรทัด {n} ไม่พบข้อความ '{it['snip']}' ใน 3 บรรทัดที่เริ่มจากบรรทัดนี้\n  จริงคือ: {lines[n-1].strip()[:100]}")
        by_line.setdefault(n, []).extend(it["text"])
    res = []
    for i, l in enumerate(lines, 1):
        if i in by_line:
            ind = re.match(r"\s*", l).group(0)
            txt = by_line[i]
            if is_xml:
                body = [t.replace("--", "—") for t in txt]
                res.append(ind + "<!--")
                res += [ind + "  " + t if t.strip() else "" for t in body]
                res.append(ind + "-->")
            else:
                res += [(ind + "// " + t) if t.strip() else (ind + "//") for t in txt]
        res.append(l)
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    open(out, "w", encoding="utf-8").write("\n".join(res))
    if is_xml:   # XML ต้องยัง "ถูกรูปแบบ" หลังแทรกคอมเมนต์ (คอมเมนต์วางผิดที่ เช่น กลาง tag จะพัง)
        import xml.etree.ElementTree as ET
        try:
            ET.parse(out)
        except ET.ParseError as e:
            sys.exit(f"ERROR XML ผลลัพธ์ไม่ถูกรูปแบบ: {e}")
    here = os.path.dirname(os.path.abspath(__file__))
    r = subprocess.run([sys.executable, os.path.join(here, "verify_same_code.py"), src, out])
    sys.exit(r.returncode)

if __name__ == "__main__":
    main(*sys.argv[1:4])
