/**
 * An extension class to a packet that keeps track of
 * data that does not need to be transferred over
 * the network.
 */

import java.util.concurrent.locks.ReentrantLock;


public class PacketTransInfo {
	private boolean ack = false;
	private long prev_send_time = 0;
	private int seqnum = 0;
	
	// This lock is used for locking the ack variable to one thread. Different
	// threads may attempt read/write the ack variable at the same time.
	ReentrantLock lock = new ReentrantLock();
	
	
	public PacketTransInfo(int seqnum) {
		this.seqnum = seqnum;
	}
	
	public int getSeqNum() {
		return seqnum;
	}
	
	public void lock() {
		lock.lock();
	}
	
	public void unlock() {
		lock.unlock();
	}
	
	public void update_send_time() {
		prev_send_time = System.currentTimeMillis();
	}
	
	public long get_send_time() {
		return prev_send_time;
	}
	
	public void setAck(boolean ack) {
		checkAckLock();
		if (!lock.isHeldByCurrentThread()) {
			throw new RuntimeException("A thread tried to access ack without locking first");
		}
		this.ack = ack;
	}
	
	public boolean getAck() {
		checkAckLock();
		if (!lock.isHeldByCurrentThread()) {
			throw new RuntimeException("A thread tried to access ack without locking first");
		}
		return ack;
	}
	
	public String toString() {
		checkAckLock();
		return "" + ack + " " + prev_send_time + " " + seqnum;
	}
	
	// ack cannot be manipulated or read unless a lock() was called. 
	public void checkAckLock() {
		if (!lock.isHeldByCurrentThread()) {
			throw new RuntimeException("A thread tried to access ack without locking first");
		}
	}
}
