import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Sender {
    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java Sender <rcv_ip> <rcv_data_port> <timeout_ms>");
            return;
        }

        String receiverIP = args[0];
        int receiverPort = Integer.parseInt(args[1]);
        int timeout = Integer.parseInt(args[2]);

        try {
            DatagramSocket socket = new DatagramSocket();
            socket.setSoTimeout(timeout);

            InetAddress address = InetAddress.getByName(receiverIP);

            DSPacket sotPacket = new DSPacket(DSPacket.TYPE_SOT,0,null);
            byte[] sotBytes = sotPacket.toBytes();

            DatagramPacket udpPacket = new DatagramPacket(sotBytes, sotBytes.length, address, receiverPort);

            boolean handshakeComplete = false;
            while (!handshakeComplete) {
                System.out.println("Sending SOT packet to " + receiverIP + ":" + receiverPort + "...");
                socket.send(udpPacket);
                try {
                    byte[] buffer = new byte[DSPacket.MAX_PACKET_SIZE];
                    DatagramPacket ackUDP = new DatagramPacket(buffer, buffer.length);
                    socket.receive(ackUDP);

                    DSPacket ackPacket = new DSPacket(ackUDP.getData());

                    if (ackPacket.getType() == DSPacket.TYPE_ACK && ackPacket.getSeqNum() == 0) {
                        System.out.println("ACK received from " + ackUDP.getAddress() + ":" + ackUDP.getPort());
                        handshakeComplete = true;
                    }
                } catch (java.net.SocketTimeoutException e) {
                    System.out.println("Timeout waiting for ACK, resending SOT...");
                }
            }
            socket.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}