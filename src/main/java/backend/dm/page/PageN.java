package backend.dm.page;

/*
对于普通数据页的管理比较简单，一个普通页面以一个 2 字节无符号数起始，表示这一页的空闲位置的偏移。剩下的部分都是实际存储的数据。
对普通页的管理，基本都是围绕着对 FSO（Free Space Offset）进行的
 */

import backend.dm.pageCache.PageCache;
import backend.util.Parser;

import java.util.Arrays;

public class PageN {
    private static final short OF_FREE = 0;
    private static final short OF_DATA = 2;
    public static final int MAX_FREE_SPACE = PageCache.PAGE_SIZE - OF_DATA;

    public static byte[] initRaw() {
        byte[] raw = new byte[PageCache.PAGE_SIZE];
        setFSO(raw, OF_DATA);
        return raw;
    }

    private static void setFSO(byte[] raw, short ofData) {
        System.arraycopy(Parser.short2Byte(ofData), 0, raw, OF_FREE, OF_DATA);
    }

    // 获取page的FSO
    public static short getFSO(Page pg) {
        return getFSO(pg.getData());
    }

    private static short getFSO(byte[] raw) {
        return Parser.parseShort(Arrays.copyOfRange(raw, 0, 2));
    }

    // 将raw插入page中，返回插入位置
    public static short insert(Page pg, byte[] raw) {
        pg.setDirty(true);
        short offset = getFSO(pg.getData());
        System.arraycopy(raw, 0, pg.getData(), offset, raw.length);
        setFSO(pg.getData(), (short) (offset + raw.length));
        return offset;
    }

    // 获取页面的空闲空间大小
    public static int getFreeSpace(Page pg) {
        return PageCache.PAGE_SIZE - (int) getFSO(pg.getData());
    }

    // 将raw插入page中的offset位置，并将page的offset设置为较大的offset
    public static void recoverInsert(Page pg, byte[] raw, short offset) {
        pg.setDirty(true);
        System.arraycopy(raw, 0, pg.getData(), offset, raw.length);

        short rawFSO = getFSO(pg.getData());
        if (rawFSO < offset + raw.length) {
            setFSO(pg.getData(), (short) (offset + raw.length));
        }
    }

    // 将raw插入page中的offset位置，不更新update
    public static void recoverUpdate(Page pg, byte[] raw, short offset) {
        pg.setDirty(true);
        System.arraycopy(raw, 0, pg.getData(), offset, raw.length);
    }

}
