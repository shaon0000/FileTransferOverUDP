import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.LinkedList;


public class Receiver {

	public static void main(String[] args) throws Exception {
		String client_name = args[0];
		int client_port = Integer.valueOf(args[1]);
		int host_port = Integer.valueOf(args[2]);
		
		InetAddress IPAddress = InetAddress.getByName(client_name);    

		String filename = args[3];
		
		DatagramPacket receivePacket = new DatagramPacket(new byte[512], 512);
		DatagramSocket socket = new DatagramSocket(host_port);
		
		File file = new File(filename);
		Writer writer = new BufferedWriter(new FileWriter(file));
		
		Writer log_writer = new BufferedWriter(new FileWriter(new File("arrival.log")));
		
		PacketHolderList packet_list = new PacketHolderList(writer);
		packet p = null;
		
		// loop until an EOT packet is received.
		while (p == null || p.getType() != 2) {
			
			packet_list.shiftIfPossible();
			
			socket.receive(receivePacket);
			p = packet.parseUDPdata(receivePacket.getData());
			if (p.getType() == 1) {
				log_writer.write(p.getSeqNum() + "\n");
				
				PacketHolder holder = packet_list.getHolder(p.getSeqNum());
				
				if (holder != null) {
					holder.setVal(new String(p.getData()));
				}
				
				Cnsl.println("RECIEVED: " + p.getSeqNum() + " " + p.getType());
			}
			
			packet return_packet = packet.createACK(p.getSeqNum());
			
			// if we received an EOT, send back an EOT instead of an ACK packet.
			if (p.getType() == 2) {
				return_packet = packet.createEOT(p.getSeqNum());
			}
			
			byte[] sendData = return_packet.getUDPdata();                   
			DatagramPacket sendPacket = new DatagramPacket(sendData, 
					sendData.length, IPAddress, client_port);
			socket.send(sendPacket);
			receivePacket = new DatagramPacket(new byte[512], 512);
			
		}

		// if the packet list isn't finished, write any packets that are left.
		packet_list.writeLeftovers();
		log_writer.close();
		writer.close();
	}

	// Works like a LinkedList, but this always contains 10 items. This is
	// the receiver window.
	public static class PacketHolderList extends LinkedList<PacketHolder> {
		
		private static final long serialVersionUID = 9028188620310126002L;
		Writer writer;
		
		public PacketHolderList(Writer writer) {
			super();
			this.writer = writer;
			
			// It adds 10 packet holder whose requires seqnum between 0 - 9.
			for (int i = 0; i < 10; i++) {
				add(new PacketHolder(i, writer));
			}
		}
		
		// The window is shiftable if a value exists for the head of the packet
		// holder.
		public boolean isShiftable() {
			return this.peekFirst().hasVal();
		}
		
		public void shiftIfPossible() throws IOException {
			while (isShiftable()) {
				PacketHolder holder = new PacketHolder(peekLast().getSeqNum() + 1, writer);
				add(holder);
				holder = this.removeFirst();
				holder.write();
			}
		}
		
		public PacketHolder getHolder(int seq_num) {
			for(PacketHolder holder: this) {
				if (holder.expected_seqnum == seq_num) {
					return holder;
				}
			}
			
			return null;
		}
		
		public void writeLeftovers() throws IOException {
			for(PacketHolder holder: this) {
				holder.write();
			}
		}
	}
	
	// Acts as a place holder for a packet. A PacketHolder can hold
	// the contents of a Packet with the same seqnum.
	public static class PacketHolder {
		int expected_seqnum;
		String val = null;
		Writer writer;
		
		public PacketHolder(int expected_seqnum, Writer writer) {
			this.expected_seqnum = expected_seqnum % packet.SeqNumModulo;
			this.writer = writer;
		}
		
		public int getSeqNum() {
			return this.expected_seqnum;
		}
		
		public void setVal(String val) {
			this.val = val;
		}
		
		public boolean hasVal() {
			return this.val != null;
		}
		
		public void write() throws IOException {
			if (val != null) {
				writer.write(val);
			}
		}
	}
}
