import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Set;
import java.util.Vector;

class HandShakeMessage implements Serializable {
    
    private String string;
    private byte [] padding;
    private int peer_id;
    
    HandShakeMessage (int pid) {
	string = "HELLO";
	padding = new byte [23];
	this.peer_id = pid;
    }
    
    int get_peerid () {
	return peer_id;
    }
    
    boolean validate_header () {
	return (string.equals("HELLO"));
    }
}

public class Connection implements Runnable {
    int clientId;
    Socket socket;

    OutputStream out;
    InputStream in;
    
    int mypid;
    int peerid;
    bitMap bitmap = peerProcess.bitmap;
    Hashtable<Integer, bitMap> peerbitmap = peerProcess.peerbitmap;
    Hashtable <Integer, Connection> connmap = peerProcess.connmap;
    Hashtable <Integer, Integer> downloadList = peerProcess.downloadList;
    Vector <Integer> interestedList = peerProcess.interestedList;
    Vector <Integer> prefNbrs = peerProcess.prefNbrs;
    //int lastChunkRequested = -1;
    private boolean sentInterested;
    private boolean unchoked;
    public int debugCount;
    public int tpCount;
	
    void setpeerid (int peerid) {
	this.peerid = peerid;
    }
	
    InputStream getInputStream () {
	return in;
    }
    
    OutputStream getOutputStream () {
	return out;
    }
    
    void HandShake () {
	
	ObjectOutputStream oos;
	ObjectInputStream ois;

	try {
	    oos = new ObjectOutputStream(out);
	    oos.writeObject(new HandShakeMessage(mypid));
	} catch (IOException e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	}
	
	HandShakeMessage inmsg = null;
	try {
	    ois = new ObjectInputStream(in);
	    inmsg = (HandShakeMessage) ois.readObject();
	} catch (IOException e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	} catch (ClassNotFoundException e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	}
	
	if (inmsg == null || !inmsg.validate_header()) {
	    //peerProcess.log("Wrong header ", mypid);
	    return;
	}
	
	int peer_id = inmsg.get_peerid();
	
	    
	this.peerid  = peer_id;
	
	synchronized (connmap) {
	
	    if (!connmap.containsKey(peer_id))  {
		connmap.put(peer_id, this);
		peerProcess.log ("Peer " +  mypid + " is connected from peer " + peerid);
	    } 
	}
	
	synchronized (downloadList) {
	    downloadList.put(peer_id, 0);
	}
    
    }
    
    Connection (Socket socket, int mypid) {
	
	this.clientId  = 0;
	this.socket = socket;
	this.mypid = mypid;
	unchoked = false;
	
	try {
	    this.out = socket.getOutputStream();
	    this.in = socket.getInputStream();
	    
	} catch (IOException e1) {
	    // TODO Auto-generated catch block
	    e1.printStackTrace();
	}
	
    }
    
    boolean getSentInterested () {
	return sentInterested;
    }
    
    void setSentInterested () {
	sentInterested = true;
    }
    
    void resetSentInterested () {
	sentInterested = false;
    }

    boolean checkAllDone () {
	synchronized (peerbitmap) {
	    return bitmap != null && bitmap.isDone() && peerbitmap != null && peerbitmap.get(peerid) != null && peerbitmap.get(peerid).isDone();
	}
    }
    
    class RecevieMessage implements Runnable {

	void bitFieldHandler (Message inmsg) {
	    
	    if (inmsg.length == 0) {
		return;
	    }
	    
	    bitMap other = new bitMap ((ArrayList <Boolean>) inmsg.payload);
	    
	    synchronized (peerbitmap) {
		peerbitmap.put(peerid, other);
		if (bitmap.compare(other)) {
		    setSentInterested();
		    Message.sendMsg(new Interested(), out);
		}
		else {
		    resetSentInterested();
		    Message.sendMsg(new NotInterested(), out);
		}
	    }
	    
	}
	
	void InterestedHandler (Message inmsg) {

	    peerProcess.log ("Peer " +  mypid + " received an 'interested' message from " + peerid);
	    synchronized (interestedList) {
		if (!interestedList.contains(peerid)) {
		    interestedList.add(peerid);
		}
	    }
			
	}
	
	void NotInterestedHandler (Message inmsg) {
	    
	    peerProcess.log ("Peer " +  mypid + " received a 'not interested' message from " + peerid);
	    synchronized (interestedList) {
		if (interestedList.contains(peerid)) {
		    interestedList.remove((Object)peerid);
		}
		synchronized (prefNbrs) {
		    if (prefNbrs.contains(peerid)) {
			prefNbrs.remove((Object)peerid);
		    }
		}
	    }
	    
	}
	
	void ChokeHandler (Message inmsg) {

	    peerProcess.log ("Peer " +  mypid + " is choked by " + peerid);
	    unchoked = false;
	}
	
	void UnChokeHandler(Message inmsg) {

	    
	    peerProcess.log ("Peer " +  mypid + " is unchoked by " + peerid);
	    bitMap ourpeerbitmap;
	    int chunkToRequest;
	    
	    unchoked = true;
	    synchronized (peerbitmap) {
		ourpeerbitmap = peerbitmap.get(peerid);
		chunkToRequest = bitmap.getRandomMissingChunk(ourpeerbitmap);
	    }
	    //lastChunkRequested = chunkToRequest;
	    if (chunkToRequest == -1) {
		resetSentInterested();
		Message.sendMsg(new NotInterested(), out);
	    } else {
		Message.sendMsg(new Request(chunkToRequest), out);
	    }
	}
	
	void RequestHandler(Message inmsg) {
	    
	    int chunkNo = (int) inmsg.payload;
	    File file = new File(System.getProperty("user.dir") + "/peer_" + mypid + "/chunk" + chunkNo);
	    FileInputStream fis;
	    byte [] data = new byte [peerProcess.chunkSize];
	    byte [] lastChunk;
	    int dataLength = 0;
	    try {
		fis = new FileInputStream (file);
		dataLength = fis.read(data);
	    } catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	    }
	    
	    if (dataLength != peerProcess.chunkSize) {
		lastChunk = new byte [dataLength];
		for (int i = 0; i < dataLength; i++) {
		    lastChunk[i] = data[i];
		}
		data = lastChunk;
	    }
	    
	    Message.sendMsg(new Piece (dataLength, data, chunkNo), out);
	    
	}
	
	void PieceHandler (Message msg) {
	    
	    int chunkNo = ((Piece)msg).getChunkNo();
	    boolean duplicate = false;	    

	    synchronized (bitmap) {
		duplicate = bitmap.chunkExists(chunkNo);
		if (!duplicate) {
		    bitmap.setChunk(chunkNo);
		}
	    }
	
	    if (duplicate) {
		synchronized (peerbitmap) {
		    
		    int chunkReq = bitmap.getRandomMissingChunk(peerbitmap.get(peerid));
		    if (chunkReq != -1) {
			Message.sendMsg(new Request(chunkReq), out);
		    }
		    return;
		}
	    }
	    peerProcess.log ("Peer " +  mypid + " has downloaded the piece " + chunkNo + " from " + peerid + " . Now the number of pieces it has is " + bitmap.getchunksSet());
	    
	    File file = new File(System.getProperty("user.dir") + "/peer_" + mypid + "/chunk" + chunkNo);
	    /*if (!bitmap.chunkExists(chunkNo)) {
		debugCount++;
	    }*/
	    try {
		FileOutputStream splitOut = new FileOutputStream(file);
		splitOut.write((byte[])((Piece)msg).getData());
		splitOut.close();
	    } catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	    }
	    
	    synchronized (downloadList) {
		int value = downloadList.get(peerid);
		downloadList.put(peerid, ++value);
	    }
	    
	    synchronized (connmap) {
		Collection <Connection> connlist = connmap.values();
		for (Connection connection : connlist) {
		    synchronized (connection.getOutputStream()) { 
			Message.sendMsg(new Have(chunkNo), connection.getOutputStream());
		    }
		}
	    }
	    
	    synchronized (peerbitmap) {
		Set<Integer> ids = peerbitmap.keySet();
		for (Integer integer : ids) {
		    bitMap pmap = peerbitmap.get((Object)integer);
		    synchronized (connmap) {
			Connection conn = connmap.get(integer);
			synchronized (conn) {
			    if (!bitmap.compare(pmap) && conn.getSentInterested()) { 
				Message.sendMsg(new NotInterested(), conn.getOutputStream());
				conn.resetSentInterested();
			    } else if (integer == peerid && unchoked) {
				int chunkReq = bitmap.getRandomMissingChunk(pmap);
				if (chunkReq != -1) {
				    Message.sendMsg(new Request(chunkReq), conn.getOutputStream());
				}
				
			    }
			}
		    }
		}
	    }
	    
	}
	
	void HaveHandler (Message msg) {
	    
	    int chunkNo = (int)msg.payload;
	    bitMap ourpeerbitmap;

	    peerProcess.log ("Peer " +  mypid + " received a 'have' message from " + peerid + " for the piece " + chunkNo);
	    synchronized (peerbitmap) {
		if (!getSentInterested() && !bitmap.chunkExists(chunkNo)) {
		    Message.sendMsg(new Interested(), out);
		    setSentInterested();
		}
		ourpeerbitmap = peerbitmap.get(peerid);
		ourpeerbitmap.setChunk(chunkNo);
	    }
	    
	}
	
	@Override
	public void run() {
	    
	    // TODO Auto-generated method stub
	    
	    while (!checkAllDone()) {
		Message msg = null;
		try {
		    msg = Message.rcvMsg(in);
		} catch (IOException e) {
		    // TODO Auto-generated catch block
		    return;
		}
		
		switch (msg.type) {
		case 0:
		    ChokeHandler(msg);
		    break;
		case 1:
		    UnChokeHandler(msg);
		    break;
		case 2:
		    InterestedHandler (msg);
		    break;
		case 3:
		    NotInterestedHandler (msg);
		    break;
		case 4:
		    HaveHandler (msg);
		    break;
		case 5:
		    bitFieldHandler (msg);
		    break;
		case 6:
		    RequestHandler (msg);
		    break;
		case 7:
		    PieceHandler (msg);
		    break;
			default:
		    break;
		}
	    }

	    //peerProcess.log ("I am out " + peerid, mypid);
	}
	
    }

    @Override
    public void run() {
	// TODO Auto-generated method stub
	HandShake();
	Message.sendMsg(new bitField(bitmap), out);
	debugCount = 0;
	tpCount = 0;
	RecevieMessage rmsg = new RecevieMessage ();
	Thread rcvThread= new Thread (rmsg);
	rcvThread.start();
	try {
	    rcvThread.join();
	} catch (InterruptedException e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	}
    }
	
}

