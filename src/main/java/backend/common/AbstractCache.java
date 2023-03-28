package backend.common;

import common.Error;

import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

//AbstractCache<T> 是一个抽象类，内部有两个抽象方法，留给实现类去实现具体的操作
public abstract class AbstractCache<T> {
    /*
    引用计数，除了普通的缓存功能，还需要另外维护一个计数。
    除此以外，为了应对多线程场景，还需要记录哪些资源正在从数据源获取中（从数据源获取资源是一个相对费时的操作）。
    于是有下面三个Map：
     */

    //    实际缓存的数据
    private HashMap<Long, T> cache;
    //    元素的引用个数
    private HashMap<Long, Integer> references;
    //    正在获取某资源的线程
    private HashMap<Long, Boolean> getting;

    //    缓存的最大缓存资源数
    private int maxResource;
    //    缓存中元素的个数
    private int count = 0;
    private Lock lock;

    public AbstractCache(int maxResource) {
        this.maxResource = maxResource;
        cache = new HashMap<>();
        references = new HashMap<>();
        lock = new ReentrantLock();
        getting = new HashMap<>();
    }

    /**
     * 当资源不在缓存时的获取行为
     */
    protected abstract T getForCache(long key) throws Exception;

    /**
     * 当资源被驱逐时的写回行为
     */
    protected abstract void releaseForCache(T obj);

    /*
    于是，在通过 get() 方法获取资源时，首先进入一个死循环，来无限尝试从缓存里获取。
    首先就需要检查这个时候是否有其他线程正在从数据源获取这个资源，如果有，就过会再来看看

    当然如果资源在缓存中，就可以直接获取并返回了，记得要给资源的引用数 +1。
    否则，如果缓存没满的话，就在 getting 中注册一下，该线程准备从数据源获取资源了。
     */

    protected T get(long key) throws Exception {
        while (true) {
            lock.lock();
            if (getting.containsKey(key)) {
                // 请求的资源正在被其他线程获取
                lock.unlock();
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    continue;
                }
                continue;
            }

            if (cache.containsKey(key)) {
                // 资源在缓存中，直接返回
                T obj = cache.get(key);
                references.put(key, references.get(key) + 1);
                lock.unlock();
                return obj;
            }

            // 尝试获取该资源
            if (maxResource > 0 && count == maxResource) {
                lock.unlock();
                throw Error.CacheFullException;
            }
            count++;
            getting.put(key, true);
            lock.unlock();
            break;
        }

        T obj = null;
        try {
            obj = getForCache(key);
        } catch (Exception e) {
            lock.lock();
            count--;
            getting.remove(key);
            lock.unlock();
            throw e;
        }

        lock.lock();
        getting.remove(key);
        cache.put(key, obj);
        references.put(key, 1);
        lock.unlock();

        return obj;
    }

    /**
     * 强行释放一个缓存
     */
    protected void release(long key) {
        lock.lock();
        try {
            int ref = references.get(key) - 1;
            if (ref == 0) {
                T obj = cache.get(key);
                releaseForCache(obj);
                references.remove(key);
                cache.remove(key);
                count--;
            } else {
                references.put(key, ref);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 关闭缓存，写回所有资源
     */
    protected void close() {
        lock.lock();
        try {
            Set<Long> keys = cache.keySet();
            for (long key : keys) {
                T obj = cache.get(key);
                releaseForCache(obj);
                references.remove(key);
                cache.remove(key);
            }
        } finally {
            lock.unlock();
        }
    }
}
