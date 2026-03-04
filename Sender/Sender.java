import java.net.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class Sender {

    public static void main(String[] args) {
        long startTime = 0;
        long endTime;
        // Usage: <rcv_ip> <rcv_data_port> <sender_ack_port> <input_file> <timeout_ms> [window_size]
        if (args.length < 5 || args.length > 6) {
            System.out.println("Usage: java Sender <rcv_ip> <rcv_data_port> <sender_ack_port> <input_file> <timeout_ms> [window_size]");
            return;
        }

        String receiverIP = args[0];
        int receiverPort = Integer.parseInt(args[1]);
        int senderAckPort = Integer.parseInt(args[2]);
        String inputFile = args[3];
        int timeout = Integer.parseInt(args[4]);
        int windowSize = 1;
        
        if (args.length == 6) {
            windowSize = Integer.parseInt(args[5]);
            if (windowSize < 1 || windowSize > 127) {
                System.out.println("Window size must be between 1 and 127");
                return;
            }
            else if (windowSize % 4 != 0){
                System.out.println("Window size must be a multiple of 4");
                return;
            }
        }

        System.out.println("Starting sender with windowSize=" + windowSize);

        try {
            DatagramSocket socket = new DatagramSocket(senderAckPort);
            socket.setSoTimeout(timeout);
            InetAddress address = InetAddress.getByName(receiverIP);

            // handshake
            DSPacket sotPacket = new DSPacket(DSPacket.TYPE_SOT, 0, null);
            DatagramPacket udpPacket = new DatagramPacket(sotPacket.toBytes(), DSPacket.MAX_PACKET_SIZE, address, receiverPort);
            boolean handshakeComplete = false;
            int failCounter = 0;
            while (!handshakeComplete) {
                if (startTime == 0) startTime = System.nanoTime();
                
                System.out.println("Sending SOT...");
                socket.send(udpPacket);
                try {
                    byte[] buffer = new byte[DSPacket.MAX_PACKET_SIZE];
                    DatagramPacket ackUDP = new DatagramPacket(buffer, buffer.length);
                    socket.receive(ackUDP);
                    DSPacket ackPacket = new DSPacket(ackUDP.getData());
                    if (ackPacket.getType() == DSPacket.TYPE_ACK && ackPacket.getSeqNum() == 0) {
                        System.out.println("Handshake complete.");
                        handshakeComplete = true;
                    }
                } catch (SocketTimeoutException ste) {
                    failCounter++;
                    System.out.println("Timeout waiting for SOT ACK...");
                    if (failCounter == 3) {
                        System.out.println("Unable to transfer file.");
                        socket.close();
                        return;
                    }
                }
            }

            // phase 2: data
            FileInputStream fis = new FileInputStream(inputFile);
            int nextSeq;
            if (windowSize <= 1) {
                nextSeq = sendStopAndWait(socket, address, receiverPort, fis, timeout);
            } else {
                nextSeq = sendGoBackN(socket, address, receiverPort, fis, timeout, windowSize);
            }
            fis.close();

            // phase 3: EOT
            int eotSeq = nextSeq % 128;
            DSPacket eotPacket = new DSPacket(DSPacket.TYPE_EOT, eotSeq, null);
            DatagramPacket eotUDP = new DatagramPacket(eotPacket.toBytes(), DSPacket.MAX_PACKET_SIZE, address, receiverPort);
            boolean eotAcked = false;
            while (!eotAcked) {
                System.out.println("Sending EOT...");
                socket.send(eotUDP);
                try {
                    byte[] buffer = new byte[DSPacket.MAX_PACKET_SIZE];
                    DatagramPacket ackUDP = new DatagramPacket(buffer, buffer.length);
                    socket.receive(ackUDP);
                    DSPacket ackPacket = new DSPacket(ackUDP.getData());
                    if (ackPacket.getType() == DSPacket.TYPE_ACK && ackPacket.getSeqNum() == eotSeq) {
                        System.out.println("Transfer complete.");
                        endTime = System.nanoTime();
                        double seconds = (endTime - startTime) / 1_000_000_000.0;
                        System.out.printf("Total Transmission Time: %.2f seconds\n", seconds);
                        
                        eotAcked = true;
                    }
                } catch (SocketTimeoutException ste) {
                    System.out.println("Timeout waiting for EOT ACK...");
                }
            }
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Sends file using stop-and-wait. Returns the next sequence number after last data packet.
     */
    private static int sendStopAndWait(DatagramSocket socket, InetAddress address, int receiverPort,
                                      FileInputStream fis, int timeout) throws Exception {
        int currentSeq = 1;
        byte[] fileBuffer = new byte[DSPacket.MAX_PAYLOAD_SIZE];
        int bytesRead;
        while ((bytesRead = fis.read(fileBuffer)) != -1) {
            byte[] dataToSend = new byte[bytesRead];
            System.arraycopy(fileBuffer, 0, dataToSend, 0, bytesRead);
            DSPacket dataPacket = new DSPacket(DSPacket.TYPE_DATA, currentSeq, dataToSend);
            DatagramPacket dataUDP = new DatagramPacket(dataPacket.toBytes(), DSPacket.MAX_PACKET_SIZE, address, receiverPort);
            boolean ackReceived = false;
            int timeoutCounter = 0;
            while (!ackReceived) {
                System.out.println("Sending DATA seq " + currentSeq);
                socket.send(dataUDP);
                try {
                    byte[] ackBuffer = new byte[DSPacket.MAX_PACKET_SIZE];
                    DatagramPacket ackUDP = new DatagramPacket(ackBuffer, ackBuffer.length);
                    socket.receive(ackUDP);
                    DSPacket ackPacket = new DSPacket(ackUDP.getData());
                    if (ackPacket.getType() == DSPacket.TYPE_ACK && ackPacket.getSeqNum() == currentSeq) {
                        System.out.println("ACK received for " + currentSeq);
                        ackReceived = true;
                    }
                } catch (SocketTimeoutException ste) {
                    timeoutCounter++;
                    System.out.println("Timeout for seq " + currentSeq);
                    if (timeoutCounter == 3) {
                        System.out.println("Unable to transfer file.");
                        socket.close();
                        fis.close();
                        System.exit(1);
                    }
                }
            }
            currentSeq = (currentSeq + 1) % 128;
        }
        return currentSeq;
    }

    /**
     * Sends file using Go-Back-N.
     * Returns next sequence number after final data packet.
     */
    private static int sendGoBackN(DatagramSocket socket, InetAddress address, int receiverPort,
                                   FileInputStream fis, int timeout, int windowSize) throws Exception {
        // read entire file into payload list (simpler than managing on-the-fly)
        List<byte[]> payloads = new ArrayList<>();
        byte[] fileBuf = new byte[DSPacket.MAX_PAYLOAD_SIZE];
        int n;
        while ((n = fis.read(fileBuf)) != -1) {
            byte[] data = new byte[n];
            System.arraycopy(fileBuf, 0, data, 0, n);
            payloads.add(data);
        }
        int totalPackets = payloads.size();
        int base = 1;
        int nextSeq = 1;
        // buffer for unacked packets
        byte[][] windowPackets = new byte[128][]; // indexed by seq number

        while (base <= totalPackets) {
            // send new packets within window, in groups of 4, permuted
            while (nextSeq < base + windowSize && nextSeq <= totalPackets) {
                List<DSPacket> group = new ArrayList<>();
                int packetsToSend = Math.min(4, (base + windowSize) - nextSeq);
                if (packetsToSend < 1) break;
                for (int i = 0; i < packetsToSend && nextSeq <= totalPackets; i++) {
                    byte[] data = payloads.get(nextSeq - 1);
                    DSPacket pkt = new DSPacket(DSPacket.TYPE_DATA, nextSeq, data);
                    group.add(pkt);
                    nextSeq = (nextSeq + 1) % 128;
                }
                List<DSPacket> permuted = ChaosEngine.permutePackets(group);
                for (DSPacket pkt : permuted) {
                    DatagramPacket udp = new DatagramPacket(pkt.toBytes(), DSPacket.MAX_PACKET_SIZE, address, receiverPort);
                    windowPackets[pkt.getSeqNum() % 128] = pkt.toBytes();
                    System.out.println("Sending DATA seq " + pkt.getSeqNum());
                    socket.send(udp);
                }
            }
            try {
                byte[] ackBuf = new byte[DSPacket.MAX_PACKET_SIZE];
                DatagramPacket ackUdp = new DatagramPacket(ackBuf, ackBuf.length);
                socket.receive(ackUdp);
                DSPacket ack = new DSPacket(ackUdp.getData());
                if (ack.getType() == DSPacket.TYPE_ACK) {
                    int ackSeq = ack.getSeqNum();
                    // advance base if ackSeq is within [base, nextSeq)
                    if (isSeqInWindow(ackSeq, base, nextSeq)) {
                        System.out.println("ACK received for " + ackSeq + "; sliding base from " + base + " to " + ((ackSeq + 1) % 128));
                        base = (ackSeq + 1) % 128;
                        if (base == 0) base = 128; // wrap maintain 1..128 semantics
                    }
                }
            } catch (SocketTimeoutException ste) {
                // timeout, retransmit all packets in window starting at base
                System.out.println("Timeout, retransmitting from base=" + base + " to " + (nextSeq - 1));
                int seq = base;
                while (seq != nextSeq) {
                    byte[] pktbytes = windowPackets[seq % 128];
                    if (pktbytes != null) {
                        DatagramPacket udp = new DatagramPacket(pktbytes, DSPacket.MAX_PACKET_SIZE, address, receiverPort);
                        System.out.println("Resending seq " + seq);
                        socket.send(udp);
                    }
                    seq = (seq + 1) % 128;
                    if (seq == 0) seq = 1;
                }
            }
        }
        // nextSeq already equals last data sequence + 1 modulo 128
        return nextSeq;
    }

    // check if seq is in [base, next) in modular arithmetic
    private static boolean isSeqInWindow(int seq, int base, int next) {
        if (base <= next) {
            return seq >= base && seq < next;
        } else {
            // wrap-around case
            return seq >= base || seq < next;
        }
    }
}