#!/usr/bin/env python3
"""纯 Python 逻辑备份（本机无 mysqldump 客户端时替代）。

用法: python3 _dump.py <输出文件> [--no-data]
产出: 可直接 source 的 SQL（含 DROP+CREATE+INSERT），UTF-8。
"""
import sys, os, datetime
import _conf



def esc(v):
    if v is None:
        return "NULL"
    if isinstance(v, (int, float)):
        return str(v)
    if isinstance(v, (bytes, bytearray)):
        return "_binary'" + v.decode("utf-8", "replace").replace("\\", "\\\\").replace("'", "\\'") + "'"
    s = str(v)
    s = s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r")
    return "'" + s + "'"


def main():
    out = sys.argv[1]
    no_data = "--no-data" in sys.argv
    c = _conf.connect()
    cur = c.cursor()
    cur.execute("show tables")
    tabs = [r[0] for r in cur.fetchall()]
    n = 0
    with open(out, "w", encoding="utf-8") as f:
        f.write("-- BONE logical backup (pure-python)\n")
        f.write("-- generated at %s\n" % datetime.datetime.now().isoformat())
        f.write("SET NAMES utf8mb4; SET FOREIGN_KEY_CHECKS=0;\n\n")
        for t in tabs:
            cur.execute("SHOW CREATE TABLE `%s`" % t)
            create = cur.fetchone()[1]
            f.write("DROP TABLE IF EXISTS `%s`;\n%s;\n\n" % (t, create))
            if no_data:
                continue
            cur.execute("SELECT * FROM `%s`" % t)
            cols = [d[0] for d in cur.description]
            rows = cur.fetchall()
            if not rows:
                continue
            collist = ",".join("`%s`" % x for x in cols)
            chunk = 500
            for i in range(0, len(rows), chunk):
                part = rows[i:i + chunk]
                vals = ",\n".join("(" + ",".join(esc(v) for v in r) + ")" for r in part)
                f.write("INSERT INTO `%s` (%s) VALUES\n%s;\n" % (t, collist, vals))
            n += len(rows)
            f.write("\n")
    print("dumped %d tables, %d rows -> %s (%.1f MB)" % (len(tabs), n, out, os.path.getsize(out) / 1048576))


if __name__ == "__main__":
    main()
