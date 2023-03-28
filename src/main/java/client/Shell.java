package client;

/*
客户端连接服务器的过程，也是背板。客户端有一个简单的 Shell，实际上只是读入用户的输入，并调用 Client.execute()
 */

import java.util.Scanner;

public class Shell {
    private Client client;

    public Shell(Client client) {
        this.client = client;
    }

    public void run() {
        Scanner sc = new Scanner(System.in);
        try {
            while (true) {
                System.out.print(">>> ");
                String statStr = sc.nextLine();
                if ("exit".equals(statStr) || "quit".equals(statStr)) {
                    break;
                }
                try {
                    byte[] res = client.execute(statStr.getBytes());
                    System.out.println(new String(res));
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }

            }
        } finally {
            sc.close();
            client.close();
        }
    }

}
