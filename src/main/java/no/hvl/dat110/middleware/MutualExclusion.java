/**
 * 
 */
package no.hvl.dat110.middleware;

import java.math.BigInteger;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import no.hvl.dat110.rpc.interfaces.NodeInterface;
import no.hvl.dat110.util.LamportClock;
import no.hvl.dat110.util.Util;

/**
 * @author tdoy
 *
 */
public class MutualExclusion {
		
	private static final Logger logger = LogManager.getLogger(MutualExclusion.class);

	/** lock variables */
	private boolean CS_BUSY = false;						// indicate to be in critical section
	private boolean WANTS_TO_ENTER_CS = false;				// indicate to want to enter CS
	private List<Message> queueack; 						// queue for acknowledged messages
	private List<Message> mutexqueue;						// queue for storing denied requests

	private LamportClock clock;								// lamport clock
	private Node node;
	
	public MutualExclusion(Node node) throws RemoteException {
		this.node = node;
		
		clock = new LamportClock();
		queueack = new ArrayList<Message>();
		mutexqueue = new ArrayList<Message>();
	}
	
	public synchronized void acquireLock() {
		CS_BUSY = true;
	}
	
	public void releaseLocks() {
		WANTS_TO_ENTER_CS = false;
		CS_BUSY = false;
	}

	public boolean doMutexRequest(Message message, byte[] updates) throws RemoteException {
		
		logger.info(node.nodename + " wants to access CS");

		// clear old acknowledgements
		queueack.clear();

		// clear waiting queue
		mutexqueue.clear();

		// increment clock
		clock.increment();

		// timestamp outgoing message
		message.setClock(clock.getClock());

		// mark that this node wants to enter CS
		WANTS_TO_ENTER_CS = true;
		
		// remove duplicate peers before voting
		List<Message> activenodes = removeDuplicatePeersBeforeVoting();

		// multicast mutex request
		multicastMessage(message, activenodes);

		// check that all voters replied
		boolean permission = areAllMessagesReturned(activenodes.size());

		if (permission) {
			acquireLock();

			// perform update on all replicas
			node.broadcastUpdatetoPeers(updates);

			// clear deferred queue
			mutexqueue.clear();
		}
		
		return permission;
	}
	
	// multicast message to other processes including self
	private void multicastMessage(Message message, List<Message> activenodes) throws RemoteException {
		
		logger.info("Number of peers to vote = " + activenodes.size());
		
		for (Message peer : activenodes) {
			NodeInterface stub = Util.getProcessStub(peer.getNodeName(), peer.getPort());
			if (stub != null) {
				stub.onMutexRequestReceived(message);
			}
		}
	}
	
	public void onMutexRequestReceived(Message message) throws RemoteException {
		
		// increment local logical clock
		clock.increment();

		// if request comes from self, acknowledge directly
		if (message.getNodeName().equals(node.getNodeName())) {
			onMutexAcknowledgementReceived(message);
			return;
		}
			
		int caseid = -1;
		
		// case 0: receiver neither in CS nor requesting it
		if (!CS_BUSY && !WANTS_TO_ENTER_CS) {
			caseid = 0;
		}
		// case 1: receiver already in CS
		else if (CS_BUSY) {
			caseid = 1;
		}
		// case 2: receiver wants to enter but is not yet in CS
		else if (WANTS_TO_ENTER_CS) {
			caseid = 2;
		}

		doDecisionAlgorithm(message, mutexqueue, caseid);
	}
	
	public void doDecisionAlgorithm(Message message, List<Message> queue, int condition) throws RemoteException {
		
		String procName = message.getNodeName();
		int port = message.getPort();
		
		switch(condition) {
		
			// Receiver is free, send OK
			case 0: {
				NodeInterface stub = Util.getProcessStub(procName, port);
				if (stub != null) {
					stub.onMutexAcknowledgementReceived(message);
				}
				break;
			}
		
			// Receiver already in CS, queue request
			case 1: {
				queue.add(message);
				break;
			}
			
			// Receiver also wants CS, compare timestamps
			case 2: {
				
				int senderClock = message.getClock();
				int myClock = node.getMessage().getClock();

				BigInteger senderID = message.getNodeID();
				BigInteger myID = node.getNodeID();

				boolean senderWins = false;

				if (senderClock < myClock) {
					senderWins = true;
				} else if (senderClock == myClock && senderID.compareTo(myID) < 0) {
					senderWins = true;
				}

				if (senderWins) {
					NodeInterface stub = Util.getProcessStub(procName, port);
					if (stub != null) {
						stub.onMutexAcknowledgementReceived(message);
					}
				} else {
					queue.add(message);
				}

				break;
			}
			
			default:
				break;
		}
		
	}
	
	public void onMutexAcknowledgementReceived(Message message) throws RemoteException {
		
		queueack.add(message);
		
	}
	
	// multicast release locks message to other processes including self
	public void multicastReleaseLocks(Set<Message> activenodes) {
		logger.info("Releasing locks from = " + activenodes.size());
		
		for (Message peer : activenodes) {
			NodeInterface stub = Util.getProcessStub(peer.getNodeName(), peer.getPort());
			if (stub != null) {
				try {
					stub.releaseLocks();
				} catch (RemoteException e) {
					// ignore unavailable peer
				}
			}
		}
	}
	
	private boolean areAllMessagesReturned(int numvoters) throws RemoteException {
		logger.info(node.getNodeName() + ": size of queueack = " + queueack.size());
		
		boolean allreturned = queueack.size() == numvoters;

		if (allreturned) {
			queueack.clear();
			return true;
		}
		
		return false;
	}
	
	private List<Message> removeDuplicatePeersBeforeVoting() {
		
		List<Message> uniquepeer = new ArrayList<Message>();
		for(Message p : node.activenodesforfile) {
			boolean found = false;
			for(Message p1 : uniquepeer) {
				if(p.getNodeName().equals(p1.getNodeName())) {
					found = true;
					break;
				}
			}
			if(!found)
				uniquepeer.add(p);
		}		
		return uniquepeer;
	}
}