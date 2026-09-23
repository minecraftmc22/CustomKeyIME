# -*- coding: utf-8 -*-
"""生成 CKIME 拼音输入法词库 -> app/src/main/assets/pinyin.txt

依赖：
    pip install jieba pypinyin

数据源：
    - jieba 的 dict.txt：词 + 词频（提供词表并按词频排序候选）
    - pypinyin：把词转成无声调拼音；单字取全部读音（多音字）

输出格式（每行）：
    pinyin<TAB>候选词1 候选词2 ...      （已按词频降序）

用法：
    cd CustomKeyIME
    python tools/gen_pinyin.py
"""
import os
from collections import Counter, defaultdict

import jieba
from pypinyin import lazy_pinyin, pinyin, Style

# 输出到 与本脚本同级的 ../app/src/main/assets/pinyin.txt
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, "..", "app", "src", "main", "assets", "pinyin.txt"))

MAXLEN = 4          # 词最大长度（字）
MAX_WORDS = 70000   # 最多收录多少词（按词频取前 N）
MAX_PER_KEY = 10    # 同一拼音最多保留几个候选


def is_cn(s):
    return all('\u4e00' <= c <= '\u9fff' for c in s)


def norm(py):
    py = py.replace('ü', 'v').replace('Ü', 'v').lower()
    if not py or any(not ('a' <= c <= 'z') for c in py):
        return None
    return py


def main():
    dict_path = os.path.join(os.path.dirname(jieba.__file__), 'dict.txt')
    print("jieba dict:", dict_path)

    entries = []
    with open(dict_path, encoding='utf-8') as f:
        for line in f:
            p = line.strip().split()
            if len(p) >= 2:
                try:
                    entries.append((p[0], int(p[1])))
                except ValueError:
                    pass
    print("总词条:", len(entries))
    entries.sort(key=lambda x: -x[1])

    cn = [(w, fr) for w, fr in entries if is_cn(w)]
    print("纯中文:", len(cn), "长度分布:", dict(sorted(Counter(len(w) for w, _ in cn).items())))

    kept = [(w, fr) for w, fr in cn if len(w) <= MAXLEN][:MAX_WORDS]
    print("收录词条:", len(kept))

    mp = defaultdict(list)
    seen = set()
    for w, fr in kept:
        if len(w) == 1:
            readings = pinyin(w, heteronym=True, style=Style.NORMAL)[0]
        else:
            readings = [''.join(lazy_pinyin(w))]
        for r in readings:
            py = norm(r)
            if py is None:
                continue
            key = (py, w)
            if key in seen:
                continue
            seen.add(key)
            if len(mp[py]) < MAX_PER_KEY:
                mp[py].append(w)

    print("拼音键数量:", len(mp))

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    lines = [py + "\t" + " ".join(mp[py]) for py in sorted(mp.keys())]
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")

    size = os.path.getsize(OUT)
    print("写入:", OUT)
    print("大小: %.2f MB" % (size / 1024 / 1024))
    for t in ["ni", "hao", "nihao", "wo", "xihuan", "shanghai", "zhongqing", "yinhang"]:
        print("  %-10s -> %s" % (t, mp.get(t, [])[:8]))


if __name__ == "__main__":
    main()
