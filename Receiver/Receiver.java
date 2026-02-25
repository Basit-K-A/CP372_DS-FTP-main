import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Receiver {
    public static void main(String[] args) {
        if(args.length < 1) {
            System.out.println("Usage: java Receiver <rcv_data_port>");
            return;
        }

        int rcvDataPort = Integer.parseInt(args[0]);

        try {
            DatagramSocket socket = new DatagramSocket(rcvDataPort);
            System.out.println("Receiver is listening on port " + rcvDataPort + "...");

            while (true) {
                byte[] buffer = new byte[DSPacket.MAX_PACKET_SIZE];
                DatagramPacket udppacket = new DatagramPacket(buffer, buffer.length);
                socket.receive(udppacket);

                System.out.println("Received packet from " + udppacket.getAddress() + ":" + udppacket.getPort());
                
                DSPacket packet = new DSPacket(udppacket.getData());

                System.out.println("Type: " + packet.getType());
                System.out.println("Seq: " + packet.getSeqNum());
                System.out.println("Length: " + packet.getLength());

                if (packet.getType() == DSPacket.TYPE_SOT) {
                    System.out.println("SOT packet received, Sending ACK...");
                    DSPacket ackPacket = new DSPacket(DSPacket.TYPE_ACK, 0, null);
                    byte[] ackBytes = ackPacket.toBytes();
                    InetAddress senderAddress = udppacket.getAddress();
                    int senderPort = udppacket.getPort();

                    DatagramPacket ackUdpPacket = new DatagramPacket(ackBytes, ackBytes.length, senderAddress, senderPort);
                    socket.send(ackUdpPacket);

                    System.out.println("ACK sent to " + senderAddress + ":" + senderPort);
                }

            }
        } catch (Exception e) {
    }
}
}