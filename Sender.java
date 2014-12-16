import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;



public class Sender {

	public static final long TIMEOUT = 1000;
	public static Writer ack_writer;
	public static Writer seqnum_writer;
	
	public static void logPacket(packet p) throws IOException {
		if (p.getType() == 0) {
			ack_writer.write(String.valueOf(p.getSeqNum()) + "\n");
		} else if (p.getType() == 1) {
			seqnum_writer.write(String.valueOf(p.getSeqNum()) + "\n");
		}
	}
	
	public static void sendPacket(packet p, InetAddress addr, int port) throws IOException {
		byte [] data = p.getUDPdata();
		DatagramSocket client_socket = new DatagramSocket();
		DatagramPacket client_packet = new DatagramPacket(data, data.length, addr, port);
		client_socket.send(client_packet);
		client_socket.close();
		logPacket(p);
	}
	
	
	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		// host address of network emulator
		// UDP port number used by emulator to listen to sender
		// UDP port number used by sender to listen for ACKs
		// name of file
		String client_address = args[0];
	    InetAddress IPAddress = InetAddress.getByName(client_address);  
		int client_port = Integer.valueOf(args[1]);
		int server_port = Integer.valueOf(args[2]);
		String filename = args[3];
		
		String acklogname = "ack.log";
		String seqnumlogname = "seqnum.log";
		
		ack_writer = new BufferedWriter(new FileWriter(new File(acklogname)));
		seqnum_writer = new BufferedWriter(new FileWriter(new File(seqnumlogname)));
		
		FileReader fr = new FileReader(filename);
		BufferedReader buffer = new BufferedReader(fr);
		
        int r;
        StringBuilder temp_string = new StringBuilder();
        PacketList packet_list = new PacketList();

        
        
        while ((r = buffer.read()) != -1) {
            char ch = (char) r;
            temp_string.append(ch);
            if (temp_string.length() == packet.maxDataLength) {
            	packet p = packet.createPacket(packet_list.size(), temp_string.toString());
            	packet_list.add(p);
            	temp_string = new StringBuilder();
            }
        }
        
        if (temp_string.length() != 0) {
        	packet p = packet.createPacket(packet_list.size(), temp_string.toString());
        	packet_list.add(p);
        	temp_string = new StringBuilder();
        }
        
        // Fire up the acknowledgment thread first.
        AcknowledgeReciever ack_recv = new AcknowledgeReciever(packet_list, server_port);
        Thread t = new Thread(ack_recv);
		t.start();
		
        repeatSendPackets(packet_list, IPAddress, client_port);
        
        packet_list.reset();
        Cnsl.println("sending EOT");
        packet_list.add(packet.createEOT(0));
        repeatSendPackets(packet_list, IPAddress, client_port);
		buffer.close();
		ack_writer.close();
		seqnum_writer.close();
	}
	
	public static void repeatSendPackets(PacketList packet_list, 
			InetAddress IPAddress, int client_port) throws IOException {

        while (packet_list.getWindowMin() < packet_list.size()) {

            packet_list.shiftWindowIfPossible();   
        	Cnsl.println("window minimum index: " + packet_list.getWindowMin() + " out of " + packet_list.size());
	        long min_time = Long.MAX_VALUE;
	        
	        for(int i = packet_list.getWindowMin(); i < packet_list.getWindowMax(); i++) {
	        	packet p = packet_list.get(i);
	        	PacketTransInfo info = p.getInfo();
	        	info.lock();
	        	long current_time = System.currentTimeMillis();

	        	if (!info.getAck() && (current_time - info.get_send_time()) > TIMEOUT) {
	            	info.update_send_time();
		        	min_time = Math.min(info.get_send_time(), min_time);
	        		sendPacket(p, IPAddress, client_port);
	        	}	
	        	info.unlock();
	        }   	
	        try {
	        	if (min_time != Long.MAX_VALUE) {
	        		Thread.sleep(Math.max(0, TIMEOUT - (System.currentTimeMillis() - min_time)));
	        	}
			} catch (InterruptedException e) {
				e.printStackTrace();
				Cnsl.println("something forced the main thread to wake up");
				
			}
        }
	}
	
	// Sender Window frame.
	public static class PacketList extends ArrayList<packet> {

		private static final long serialVersionUID = -3357605339505771006L;
		private int window_id;
		
		public void reset() {
			clear();
			window_id = 0;
		}
		
		public int getWindowMax() {
			return Math.min(window_id + 10, size());
		}
		
		public int getWindowMin() {
			return window_id;
		}
		
		public packet getPacketInWindow(int seq_num) {
			for (int i = getWindowMin(); i < getWindowMax(); i++) {
				if (get(i).getSeqNum() == seq_num) {
					return get(i);
				}
			}
			
			Cnsl.println("WARNING: packet in window method returning null");
			Cnsl.println("SEARCHED: " + seq_num);
			Cnsl.println("min, max: " + getWindowMin() + " " + getWindowMax());
			return null;
		}

			// window is shiftable if the item at window_id was acknowledged.
		public boolean windowIsShiftable() {
			if (size() == 0) {
				return false;
			}
			
			if (window_id == size()) {
				return false;
			}
			
			packet p = get(getWindowMin());
			p.getInfo().lock();
			if (p.getInfo().getAck()) {
				p.getInfo().unlock();
				return true;
			} else {
				p.getInfo().unlock();
				return false;
			}
		}
		
		public void shiftWindowIfPossible() {
			while (windowIsShiftable()) {
				window_id++;
				Cnsl.println("window shifted to " + window_id);
			}
		}
		
	}
	
	// Thread listens for any incoming packets and flags variables accordingly.
	private static class AcknowledgeReciever implements Runnable {
		private PacketList packet_list;
		private int port;
		
		public AcknowledgeReciever(PacketList packet_list, int port) {
			this.packet_list = packet_list;
			this.port = port;
		}

		@Override
		public void run() {
			DatagramSocket receiveSocket;
			packet ack_packet = null;
			try {
				receiveSocket = new DatagramSocket(port);
			} catch (SocketException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
				return;
			}
			while (ack_packet == null || ack_packet.getType() != 2) {
				
				byte[] receiveData = new byte[512];  
				DatagramPacket receivePacket = new DatagramPacket(receiveData, 
						receiveData.length);
					
				try {
					receiveSocket.receive(receivePacket);
				} catch (IOException e) {
					e.printStackTrace();
					Cnsl.println("failed to recieve anything");
					return;
				}
	
				try {
					ack_packet = packet.parseUDPdata(receivePacket.getData());
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					return;
				}
				
				try {
					logPacket(ack_packet);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					return;
				}
				packet data_packet = packet_list.getPacketInWindow(ack_packet.getSeqNum());
				Cnsl.println("RECEIVED ACK: " + ack_packet.getSeqNum()); 
				
				data_packet.getInfo().lock();
				data_packet.getInfo().setAck(true);
				data_packet.getInfo().unlock();
				
			}
			
			if (ack_packet.getType() != 2) {
				Cnsl.println("ERROR: terminated with non EOT packet");
			} else {
				Cnsl.println("done");
			}
			receiveSocket.close();
			
		}
		
	}

}
