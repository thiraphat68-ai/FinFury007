#!/usr/bin/env python3
"""ตรวจว่าไฟล์ที่คอมเมนต์แล้ว "โค้ดเหมือนไฟล์จริงทุกตัวอักษร"
วิธีใช้:  python3 verify_same_code.py <ไฟล์จริง> <ไฟล์ที่คอมเมนต์>
หลักการ: ตัดคอมเมนต์ (// , /* */ และ <!-- --> สำหรับ XML) ออกจากทั้งสองไฟล์
แล้วตัดช่องว่างทั้งหมด จากนั้นเทียบกัน ถ้าตรงกันแปลว่าไม่มีการแก้โค้ดจริง"""
import sys, re

def strip_java(s):
    out, i, n = [], 0, len(s)
    while i < n:
        c = s[i]
        if s.startswith("//", i):
            while i < n and s[i] != "\n": i += 1
        elif s.startswith("/*", i):
            j = s.find("*/", i + 2); i = n if j < 0 else j + 2
        elif c == '"' or c == "'":
            q = c; out.append(c); i += 1
            while i < n and s[i] != q:
                if s[i] == "\\": out.append(s[i]); i += 1
                out.append(s[i]); i += 1
            out.append(q); i += 1
        else:
            out.append(c); i += 1
    return "".join(out)

def strip_xml(s):
    return re.sub(r"<!--.*?-->", "", s, flags=re.S)

def norm(path):
    s = open(path, encoding="utf-8").read()
    s = strip_xml(s) if path.endswith(".xml") else strip_java(s)
    return re.sub(r"\s+", "", s)

if __name__ == "__main__":
    a, b = norm(sys.argv[1]), norm(sys.argv[2])
    if a == b:
        print("OK  โค้ดเหมือนต้นฉบับ:", sys.argv[2]); sys.exit(0)
    k = next((i for i in range(min(len(a), len(b))) if a[i] != b[i]), min(len(a), len(b)))
    print("DIFF ที่ตำแหน่ง", k, "\n ต้นฉบับ:", a[max(0,k-60):k+80], "\n สำเนา  :", b[max(0,k-60):k+80])
    sys.exit(1)
