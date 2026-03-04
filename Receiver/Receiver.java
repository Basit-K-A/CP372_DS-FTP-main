import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.io.FileOutputStream;
import java.util.Arrays;

public class Receiver {
    public static void main(String[] args) {
        if(args.length != 5) {
            System.out.println("Usage: java Receiver <sender_ip> <sender_ack_port> <rcv_data_port> <output_file> <RN>");
            return;
        }

        String senderIP = args[0];
        int senderAckPort = Integer.parseInt(args[1]);
        int rcvDataPort = Integer.parseInt(args[2]);
        String outputFile = args[3];
        int rn = Integer.parseInt(args[4]);

        System.out.println("Receiver listening on port " + rcvDataPort + ", sending ACKs to " + senderIP + ":" + senderAckPort + ", RN=" + rn);

        try {
            DatagramSocket socket = new DatagramSocket(rcvDataPort);
            InetAddress senderAddr = InetAddress.getByName(senderIP);
            int ackCount = 0; // for ChaosEngine.shouldDrop

            System.out.println("Receiver is listening on port " + rcvDataPort + "...");

            // Open output file and track expected sequence outside the receive loop
            FileOutputStream fos = new FileOutputStream(outputFile);
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
                    fos = new FileOutputStream(outputFile);
                    expectedSeq = 1;

                    DSPacket ackPacket = new DSPacket(DSPacket.TYPE_ACK, 0, null);
                    sendAck(socket, senderAddr, senderAckPort, ackPacket, ackCount++, rn);
                    continue;
                }

                if (packet.getType() == DSPacket.TYPE_EOT) {
                    System.out.println("EOT received — sending ACK and finishing.");
                    DSPacket ackPacket = new DSPacket(DSPacket.TYPE_ACK, packet.getSeqNum(), null);
                    sendAck(socket, senderAddr, senderAckPort, ackPacket, ackCount++, rn);
                    fos.close();
                    System.out.println("Transfer complete. Output saved to " + outputFile);
                    return;
                }

                if (packet.getType() == DSPacket.TYPE_DATA) {
                    int seq = packet.getSeqNum();

                    if (seq == expectedSeq) {
                        fos.write(packet.getPayload());
                        System.out.println("DATA received in order: " + seq);

                        DSPacket ackPacket = new DSPacket(DSPacket.TYPE_ACK, seq, null);
                        sendAck(socket, senderAddr, senderAckPort, ackPacket, ackCount++, rn);

                        expectedSeq = (expectedSeq + 1) % 128;

                    } else {
                        System.out.println("Out of order packet: " + seq);

                        int lastInOrder = (expectedSeq - 1 + 128) % 128;
                        DSPacket ackPacket = new DSPacket(DSPacket.TYPE_ACK, lastInOrder, null);
                        sendAck(socket, senderAddr, senderAckPort, ackPacket, ackCount++, rn);
                    }
                }
            }
        } catch (Exception e) {
    }
}

private static void sendAck(DatagramSocket socket, InetAddress senderAddr, int senderAckPort,
                            DSPacket ackPacket, int ackCount, int rn) throws Exception {

    if (ChaosEngine.shouldDrop(ackCount, rn)) {
        System.out.println("ACK dropped (chaos): " + ackPacket.getSeqNum());
        return;
    }

    byte[] ackBytes = ackPacket.toBytes();

    DatagramPacket ackUDP = new DatagramPacket(
            ackBytes,
            ackBytes.length,
            senderAddr,
            senderAckPort
    );

    socket.send(ackUDP);
    System.out.println("ACK sent: " + ackPacket.getSeqNum());
}

}