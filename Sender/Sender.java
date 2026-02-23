import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Sender {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java Sender <rcv_ip> <rcv_data_port>");
            return;
        }

        String receiverIP = args[0];
        int receiverPort = Integer.parseInt(args[1]);

        try {
            DatagramSocket socket = new DatagramSocket();
            InetAddress address = InetAddress.getByName(receiverIP);

            DSPacket sotPacket = new DSPacket(DSPacket.TYPE_SOT,0,null);
            byte[] bytesToSend = sotPacket.toBytes();

            DatagramPacket udpPacket = new DatagramPacket(bytesToSend, bytesToSend.length, address, receiverPort);

            socket.send(udpPacket);

            System.out.println("SOT packet sent to " + receiverIP + ":" + receiverPort);

            socket.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}