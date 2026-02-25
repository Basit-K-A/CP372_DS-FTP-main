import java.net.DatagramPacket;
import java.net.DatagramSocket;
//import java.net.InetAddress;
import java.io.FileOutputStream;
import java.util.Arrays;

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

            // Open output file and track expected sequence outside the receive loop
            FileOutputStream fos = new FileOutputStream("output.bin");
            int expectedSeq = 1; // First DATA packet is Seq 1

            while (true) {
                byte[] buffer = new byte[DSPacket.MAX_PACKET_SIZE];
                DatagramPacket udppacket = new DatagramPacket(buffer, buffer.length);
                socket.receive(udppacket);

                System.out.println("Received packet from " + udppacket.getAddress() + ":" + udppacket.getPort());

                // Parse only the bytes actually received
                byte[] pktBytes = Arrays.copyOf(udppacket.getData(), udppacket.getLength());
                DSPacket packet = new DSPacket(pktBytes);

                System.out.println("Type: " + packet.getType());
                System.out.println("Seq: " + packet.getSeqNum());
                System.out.println("Length: " + packet.getLength());

                if (packet.getType() == DSPacket.TYPE_SOT) {
                    System.out.println("SOT received — sending ACK(0) and resetting state.");
                    // Reset output file/state for a new transfer
                    fos.close();
                    fos = new FileOutputStream("output.bin");
                    expectedSeq = 1;

                    DSPacket ackPacket = new DSPacket(DSPacket.TYPE_ACK, 0, null);
                    sendAck(socket, udppacket, ackPacket);
                    continue;
                }

                if (packet.getType() == DSPacket.TYPE_EOT) {
                    System.out.println("EOT received — sending ACK and finishing.");
                    DSPacket ackPacket = new DSPacket(DSPacket.TYPE_ACK, packet.getSeqNum(), null);
                    sendAck(socket, udppacket, ackPacket);
                    fos.close();
                    System.out.println("Transfer complete. Output saved to output.bin");
                    break;
                }

                if (packet.getType() == DSPacket.TYPE_DATA) {
                    int seq = packet.getSeqNum();

                    if (seq == expectedSeq) {
                        fos.write(packet.getPayload());
                        System.out.println("DATA received in order: " + seq);

                        DSPacket ackPacket = new DSPacket(DSPacket.TYPE_ACK, seq, null);
                        sendAck(socket, udppacket, ackPacket);

                        expectedSeq = (expectedSeq + 1) % 128;

                    } else {
                        System.out.println("Out of order packet: " + seq);

                        int lastInOrder = (expectedSeq - 1 + 128) % 128;
                        DSPacket ackPacket = new DSPacket(DSPacket.TYPE_ACK, lastInOrder, null);
                        sendAck(socket, udppacket, ackPacket);
                    }
                }
            }
        } catch (Exception e) {
    }
}

private static void sendAck(DatagramSocket socket,
                            DatagramPacket receivedPacket,
                            DSPacket ackPacket) throws Exception {

    byte[] ackBytes = ackPacket.toBytes();

    DatagramPacket ackUDP = new DatagramPacket(
            ackBytes,
            ackBytes.length,
            receivedPacket.getAddress(),
            receivedPacket.getPort()
    );

    socket.send(ackUDP);
}

}