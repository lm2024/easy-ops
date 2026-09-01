package com.ops.agent.filemgr;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * ZIP 分卷流式写入器（兼容 7-Zip / WinRAR 分卷解压）。
 * <p>
 * 旧式命名：生成 base.z01 / base.z02 / ... / base.zip（最后卷含 central directory 与 EOCD）。
 * 7-Zip 打开首卷 .z01 或末卷 .zip 均会自动合并所有分卷还原完整文件。
 * 单个大 entry 的数据可跨多个分卷；单卷时仅生成 base.zip（普通 zip）。
 * <p>
 * 采用 Deflater level 9（与 gzip 同算法，ZIP 最大压缩），流式 8KB 缓冲写盘，
 * 内存占用 O(缓冲)，可安全压缩 10GB+ 文件。
 * 每个 entry 用 data descriptor（LFH/CD 均置 bit3），压缩完成后再回填 CRC/大小。
 */
public class SplitZipWriter implements Closeable {

    private static final int LFH_SIG  = 0x04034b50;
    private static final int CD_SIG   = 0x02014b50;
    private static final int EOCD_SIG = 0x06054b50;
    private static final int DESC_SIG = 0x08074b50;
    private static final int FLAG_UTF8 = 0x0800;   // 文件名 UTF-8
    private static final int FLAG_DESC = 0x0008;   // data descriptor

    /** entry 名称的最小/常见长度缓冲（LFH 30 字节 + 名称） */
    private static final int LFH_FIXED = 30;

    private final String partPath;   // 分卷前缀（xxx.zip）
    private final long partSize;     // 每卷字节数
    private final List<File> parts = new ArrayList<>();
    private final List<EntryInfo> entries = new ArrayList<>();

    private OutputStream out;
    private int diskNo;              // 当前分卷号（0 起）
    private long offset;             // 当前分卷内已写字节
    private EntryInfo cur;
    private boolean closed;

    private final byte[] tmp = new byte[8192];

    public SplitZipWriter(String partPath, long partSize) throws IOException {
        this.partPath = partPath;
        this.partSize = partSize;
        newPart();
    }

    /** 切到下一个分卷（旧式命名 .z01/.z02/...，最后卷由 close() 重命名为 .zip）。 */
    private void newPart() throws IOException {
        if (out != null) {
            out.close();
        }
        String name = String.format("%s.z%02d", partPath, diskNo + 1);
        out = new FileOutputStream(new File(name));
        parts.add(new File(name));
        offset = 0;
    }

    /** 当前卷剩余空间不足以容纳 need 字节时切卷（首卷不切）。 */
    private void ensure(long need) throws IOException {
        if (offset > 0 && offset + need > partSize) {
            diskNo++;
            newPart();
        }
    }

    /** 开始一个 entry，准备接收 deflate 数据。mtime 用于 DOS 时间字段（<=0 用 0）。 */
    public void beginEntry(String name) throws IOException {
        beginEntry(name, 0L);
    }

    /** 开始一个 entry，准备接收 deflate 数据。 */
    public void beginEntry(String name, long mtime) throws IOException {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        ensure(LFH_FIXED + nameBytes.length);
        cur = new EntryInfo();
        cur.name = name;
        cur.nameBytes = nameBytes;
        cur.diskStart = diskNo;
        cur.offset = offset;
        cur.crc = new CRC32();
        cur.deflater = new Deflater(9, true); // raw deflate（zip 无 zlib 头）
        long dos = mtime > 0 ? ((long) dosDate(mtime) << 16) | dosTime(mtime) : 0L;
        cur.dos = dos;

        writeInt(LFH_SIG);
        writeShort(20);                 // version needed
        writeShort(FLAG_UTF8 | FLAG_DESC);
        writeShort(8);                  // method=deflate
        writeInt((int) dos);            // mod time + date
        writeInt(0);                    // crc=0（待回填）
        writeInt(0);                    // csize=0
        writeInt(0);                    // usize=0
        writeShort(nameBytes.length);
        writeShort(0);                  // extra len
        out.write(nameBytes);
        offset += nameBytes.length;
    }

    /** 写入一段原始数据（会按需切卷并更新 CRC / 统计）。 */
    public void writeData(byte[] buf, int len) throws IOException {
        if (cur == null) {
            throw new IOException("未 beginEntry");
        }
        cur.crc.update(buf, 0, len);
        cur.rawSize += len;
        cur.deflater.setInput(buf, 0, len);
        while (!cur.deflater.needsInput()) {
            int n = cur.deflater.deflate(tmp, 0, tmp.length);
            if (n > 0) {
                writeCompressed(tmp, 0, n);
            } else if (!cur.deflater.needsInput()) {
                continue; // 有输入但暂无输出，继续推进 deflater（zlib 保证最终输出或 needsInput）
            } else {
                break;
            }
        }
    }

    private void writeCompressed(byte[] buf, int off, int len) throws IOException {
        ensure(len);
        out.write(buf, off, len);
        offset += len;
        cur.compSize += len;
    }

    /** 结束当前 entry，写出 data descriptor 并登记。 */
    public void finishEntry() throws IOException {
        if (cur == null) {
            return;
        }
        cur.deflater.finish();
        while (!cur.deflater.finished()) {
            int n = cur.deflater.deflate(tmp, 0, tmp.length);
            if (n > 0) {
                writeCompressed(tmp, 0, n);
            }
        }
        cur.deflater.end();
        ensure(20); // signature+crc+csize+usize
        writeInt(DESC_SIG);
        writeInt((int) cur.crc.getValue());
        writeInt((int) cur.compSize);
        writeInt((int) cur.rawSize);
        entries.add(cur);
        cur = null;
    }

    /** 写出 central directory + EOCD 并关闭所有分卷。 */
    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        if (cur != null) {
            finishEntry();
        }
        int cdDisk = diskNo;
        long cdOffset = offset;
        long cdSize = 0;
        // 预占空间（CD 一般仅每 entry ~50 字节，远小于分卷）
        for (EntryInfo ei : entries) {
            cdSize += 46 + ei.nameBytes.length;
        }
        ensure(cdSize);
        for (EntryInfo ei : entries) {
            long dos = ei.dos;
            writeInt(CD_SIG);
            writeShort(20);            // version made by
            writeShort(20);            // version needed
            writeShort(FLAG_UTF8 | FLAG_DESC); // flags 必须与 LFH 一致（保留 bit3 data descriptor）
            writeShort(8);             // method
            writeInt((int) dos);       // time+date
            writeInt((int) ei.crc.getValue());
            writeInt((int) ei.compSize);
            writeInt((int) ei.rawSize);
            writeShort(ei.nameBytes.length);
            writeShort(0);             // extra
            writeShort(0);             // comment
            writeShort(ei.diskStart);  // disk number start
            writeShort(0);             // internal attrs
            writeInt(0);               // external attrs
            writeInt((int) ei.offset); // local header offset（相对起始卷）
            out.write(ei.nameBytes);
            offset += ei.nameBytes.length;
        }
        ensure(22); // EOCD
        writeInt(EOCD_SIG);
        writeShort(diskNo);            // 本 EOCD 所在卷
        writeShort(cdDisk);            // CD 起始卷
        writeShort(entries.size());
        writeShort(entries.size());
        writeInt((int) cdSize);
        writeInt((int) cdOffset);
        writeShort(0);                 // comment len
        out.close();
        out = null;

        // 旧式分卷命名（7-Zip 兼容）：最后卷必须命名为 xxx.zip
        File last = parts.get(parts.size() - 1);
        File finalZip = new File(partPath + ".zip");
        if (!last.getAbsolutePath().equalsIgnoreCase(finalZip.getAbsolutePath())) {
            if (finalZip.exists()) {
                finalZip.delete();
            }
            if (last.renameTo(finalZip)) {
                parts.set(parts.size() - 1, finalZip);
            }
        }
    }

    /** 已生成的分卷文件列表。 */
    public List<File> getParts() {
        return new ArrayList<>(parts);
    }

    private void writeInt(int v) throws IOException {
        out.write(v & 0xFF);
        out.write((v >>> 8) & 0xFF);
        out.write((v >>> 16) & 0xFF);
        out.write((v >>> 24) & 0xFF);
        offset += 4;
    }

    private void writeShort(int v) throws IOException {
        out.write(v & 0xFF);
        out.write((v >>> 8) & 0xFF);
        offset += 2;
    }

    /** DOS 时间（低 16 位）与日期（高 16 位）转换。 */
    private static int dosTime(long millis) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(millis);
        return (c.get(java.util.Calendar.HOUR_OF_DAY) << 11)
                | (c.get(java.util.Calendar.MINUTE) << 5)
                | (c.get(java.util.Calendar.SECOND) >> 1);
    }

    private static int dosDate(long millis) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(millis);
        return ((c.get(java.util.Calendar.YEAR) - 1980) << 9)
                | ((c.get(java.util.Calendar.MONTH) + 1) << 5)
                | c.get(java.util.Calendar.DAY_OF_MONTH);
    }

    /** 单个 zip entry 的元信息（在 close 时写入 central directory）。 */
    private static class EntryInfo {
        String name;
        byte[] nameBytes;
        int diskStart;
        long offset;
        long dos;
        CRC32 crc;
        Deflater deflater;
        long compSize;
        long rawSize;
    }
}
