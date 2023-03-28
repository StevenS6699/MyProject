package backend.vm;

/*
VM 层通过 VersionManager 接口，向上层提供功能
同时，VM 的实现类还被设计为 Entry 的缓存，需要继承 AbstractCache<Entry>。需要实现的获取到缓存和从缓存释放的方法很简单
 */

import backend.dm.DataManager;
import backend.tm.TransactionManager;

public interface VersionManager {
    byte[] read(long xid, long uid) throws Exception;

    long insert(long xid, byte[] data) throws Exception;

    boolean delete(long xid, long uid) throws Exception;

    long begin(int level);

    void commit(long xid) throws Exception;

    void abort(long xid);

    public static VersionManager newVersionManager(TransactionManager tm, DataManager dm) {
        return new VersionManagerImpl(tm, dm);
    }
}
