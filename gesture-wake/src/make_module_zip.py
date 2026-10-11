#!/usr/bin/env python3
"""把 KernelSU 模块目录打成可直接安装的 zip。

    python make_module_zip.py <module_dir> <out.zip>

踩过的坑（必须遵守）：
  * 压缩包内必须是**正斜杠**路径。Windows 的 Compress-Archive 会写反斜杠，
    设备上 unzip 会把 `product/overlay/X.apk` 解成一个叫 `productoverlayX.apk` 的扁平文件，
    安装脚本随即报 `No such file or directory` —— 模块装不上。
  * `.sh` 要 0755，其余 0644（service.sh 不可执行的话开机根本不会跑）。
  * 时间戳固定，方便复现（同一份内容每次打出同样的字节）。
"""
import os
import sys
import zipfile


def main():
    if len(sys.argv) != 3:
        print(__doc__)
        return 2
    src, out = sys.argv[1], sys.argv[2]
    if not os.path.isdir(src):
        print("模块目录不存在:", src)
        return 2
    out = os.path.abspath(out)
    os.makedirs(os.path.dirname(out), exist_ok=True)

    count = 0
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for dirpath, dirnames, filenames in os.walk(src):
            dirnames.sort()
            filenames.sort()
            for name in filenames:
                full = os.path.join(dirpath, name)
                if os.path.abspath(full) == out:      # 别把正在写的输出打进去
                    continue
                arc = os.path.relpath(full, src).replace("\\", "/")
                mode = 0o100755 if name.endswith(".sh") else 0o100644
                info = zipfile.ZipInfo(arc, date_time=(1980, 1, 1, 0, 0, 0))
                info.external_attr = mode << 16
                info.compress_type = zipfile.ZIP_DEFLATED
                with open(full, "rb") as f:
                    z.writestr(info, f.read())
                print("  %-52s %7d  0o%o" % (arc, os.path.getsize(full), mode))
                count += 1

    print("== %s  (%d 个文件, %d bytes)" % (out, count, os.path.getsize(out)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
