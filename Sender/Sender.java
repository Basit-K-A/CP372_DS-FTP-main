import java.net.*;
import java.io.*;

public class Sender {

    public static void main(String[] args) {

        if (args.length < 4) {
            System.out.println("Usage: java Sender <rcv_ip> <rcv_data_port> <input_file> <timeout_ms>");
            return;
        }

        String receiverIP = args[0];
        int receiverPort = Integer.parseInt(args[1]);
        String inputFile = args[2];
        int timeout = Integer.parseInt(args[3]);

        try {

            DatagramSocket socket = new DatagramSocket();
            socket.setSoTimeout(timeout);

            InetAddress address = InetAddress.getByName(receiverIP);

            // =========================
            // PHASE 1 — HANDSHAKE
            // =========================

            DSPacket sotPacket = new DSPacket(DSPacket.TYPE_SOT, 0, null);
            byte[] sotBytes = sotPacket.toBytes();

            DatagramPacket udpPacket =
                    new DatagramPacket(sotBytes, sotBytes.length, address, receiverPort);

            boolean handshakeComplete = false;

            while (!handshakeComplete) {

                System.out.println("Sending SOT...");
                socket.send(udpPacket);

                try {
                    byte[] buffer = new byte[DSPacket.MAX_PACKET_SIZE];
                    DatagramPacket ackUDP =
                            new DatagramPacket(buffer, buffer.length);

                    socket.receive(ackUDP);

                    DSPacket ackPacket =
                            new DSPacket(ackUDP.getData());

                    if (ackPacket.getType() == DSPacket.TYPE_ACK &&
                        ackPacket.getSeqNum() == 0) {

                        System.out.println("Handshake complete.");
                        handshakeComplete = true;
                    }

                } catch (SocketTimeoutException e) {
                    System.out.println("Timeout waiting for SOT ACK...");
                }
            }

            // =========================
            // PHASE 2 — STOP AND WAIT
            // =========================

            FileInputStream fis = new FileInputStream(inputFile);

            int currentSeq = 1;
            byte[] fileBuffer = new byte[DSPacket.MAX_PAYLOAD_SIZE];
            int bytesRead;

            while ((bytesRead = fis.read(fileBuffer)) != -1) {

                byte[] dataToSend = new byte[bytesRead];
                System.arraycopy(fileBuffer, 0, dataToSend, 0, bytesRead);

                DSPacket dataPacket =
                        new DSPacket(DSPacket.TYPE_DATA, currentSeq, dataToSend);

                byte[] packetBytes = dataPacket.toBytes();

                DatagramPacket dataUDP =
                        new DatagramPacket(packetBytes, packetBytes.length, address, receiverPort);

                boolean ackReceived = false;
                int timeoutCounter = 0;

                while (!ackReceived) {

                    System.out.println("Sending DATA seq " + currentSeq);
                    socket.send(dataUDP);

                    try {
                        byte[] ackBuffer = new byte[DSPacket.MAX_PACKET_SIZE];
                        DatagramPacket ackUDP =
                                new DatagramPacket(ackBuffer, ackBuffer.length);

                        socket.receive(ackUDP);

                        DSPacket ackPacket =
                                new DSPacket(ackUDP.getData());

                        if (ackPacket.getType() == DSPacket.TYPE_ACK &&
                            ackPacket.getSeqNum() == currentSeq) {

                            System.out.println("ACK received for " + currentSeq);
                            ackReceived = true;
                        }

                    } catch (SocketTimeoutException e) {

                        timeoutCounter++;
                        System.out.println("Timeout for seq " + currentSeq);

                        if (timeoutCounter == 3) {
                            System.out.println("Unable to transfer file.");
                            socket.close();
                            fis.close();
                            return;
                        }
                    }
                }

                currentSeq = (currentSeq + 1) % 128;
            }

            fis.close();

            // =========================
            // PHASE 3 — EOT
            // =========================

            DSPacket eotPacket =
                    new DSPacket(DSPacket.TYPE_EOT, currentSeq, null);

            byte[] eotBytes = eotPacket.toBytes();

            DatagramPacket eotUDP =
                    new DatagramPacket(eotBytes, eotBytes.length, address, receiverPort);

            boolean eotAcked = false;

            while (!eotAcked) {

                System.out.println("Sending EOT...");
                socket.send(eotUDP);

                try {
                    byte[] buffer = new byte[DSPacket.MAX_PACKET_SIZE];
                    DatagramPacket ackUDP =
                            new DatagramPacket(buffer, buffer.length);

                    socket.receive(ackUDP);

                    DSPacket ackPacket =
                            new DSPacket(ackUDP.getData());

                    if (ackPacket.getType() == DSPacket.TYPE_ACK &&
                        ackPacket.getSeqNum() == currentSeq) {

                        System.out.println("Transfer complete.");
                        eotAcked = true;
                    }

                } catch (SocketTimeoutException e) {
                    System.out.println("Timeout waiting for EOT ACK...");
                }
            }

            socket.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}