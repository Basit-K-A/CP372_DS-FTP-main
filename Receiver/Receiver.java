import java.net.DatagramPacket;
import java.net.DatagramSocket;

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

                if (packet.getLength() > 0) {
                    System.out.println("Payload bytes received: " + packet.getPayload().length);
                }
                System.out.println("-----------------------------------");

            }
        } catch (Exception e) {
    }
}
}