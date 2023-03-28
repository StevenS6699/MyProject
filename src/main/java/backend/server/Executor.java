package backend.server;

/*
处理的核心是 Executor 类，Executor 调用 Parser 获取到对应语句的结构化信息对象，
并根据对象的类型，调用 TBM 的不同方法进行处理。具体不再赘述

Launcher 类，则是服务器的启动入口。
这个类解析了命令行参数。很重要的参数就是 -open 或者 -create。Launcher 根据两个参数，
来决定是创建数据库文件，还是启动一个已有的数据库。
 */

import backend.parser.Parser;
import backend.parser.statement.*;
import backend.tbm.BeginRes;
import backend.tbm.TableManager;
import common.Error;

public class Executor {
    private long xid;
    TableManager tbm;

    public Executor(TableManager tbm) {
        this.tbm = tbm;
        this.xid = 0;
    }

    public void close() {
        if (xid != 0) {
            System.out.println("Abnormal Abort: " + xid);
            tbm.abort(xid);
        }
    }

    public byte[] execute(byte[] sql) throws Exception {
        System.out.println("Execute: " + new String(sql));
        Object stat = Parser.Parse(sql);
        if (Begin.class.isInstance(stat)) {
            if (xid != 0) {
                throw Error.NestedTransactionException;
            }
            BeginRes r = tbm.begin((Begin) stat);
            xid = r.xid;
            return r.result;
        } else if (Commit.class.isInstance(stat)) {
            if (xid == 0) {
                throw Error.NoTransactionException;
            }
            byte[] res = tbm.commit(xid);
            xid = 0;
            return res;
        } else if (Abort.class.isInstance(stat)) {
            if (xid == 0) {
                throw Error.NoTransactionException;
            }
            byte[] res = tbm.abort(xid);
            xid = 0;
            return res;
        } else {
            return execute2(stat);
        }
    }

    private byte[] execute2(Object stat) throws Exception {
        boolean tmpTransaction = false;
        Exception e = null;
        if (xid == 0) {
            tmpTransaction = true;
            BeginRes r = tbm.begin(new Begin());
            xid = r.xid;
        }
        try {
            byte[] res = null;
            if (Show.class.isInstance(stat)) {
                res = tbm.show(xid);
            } else if (Create.class.isInstance(stat)) {
                res = tbm.create(xid, (Create) stat);
            } else if (Select.class.isInstance(stat)) {
                res = tbm.read(xid, (Select) stat);
            } else if (Insert.class.isInstance(stat)) {
                res = tbm.insert(xid, (Insert) stat);
            } else if (Delete.class.isInstance(stat)) {
                res = tbm.delete(xid, (Delete) stat);
            } else if (Update.class.isInstance(stat)) {
                res = tbm.update(xid, (Update) stat);
            }
            return res;
        } catch (Exception e1) {
            e = e1;
            throw e;
        } finally {
            if (tmpTransaction) {
                if (e != null) {
                    tbm.abort(xid);
                } else {
                    tbm.commit(xid);
                }
                xid = 0;
            }
        }
    }

}
