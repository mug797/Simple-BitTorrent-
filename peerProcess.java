import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.Properties;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.sql.Timestamp;
import java.util.Date;

class bitMap  {
    
    private ArrayList <Boolean> map;
    private int chunksSet;
    
    bitMap (boolean fileExists, int noChunks) {

	map = new ArrayList <Boolean> ();
	chunksSet = 0;
	
	if (fileExists) {
	    chunksSet = noChunks;
	}
	
	for (int i = 0; i < noChunks; i++) {
	    map.add(fileExists);
	}
    
    }

    bitMap (ArrayList <Boolean> map) {
	this.map  = map;
	chunksSet = 0;
	for (Boolean val : map) {
	    if (val) {chunksSet++;}
	}
    }
    
    public synchronized boolean compare(bitMap other) {
	// TODO Auto-generated method stub
	//if (other.getbitMap().size() != map.size())
	//    return false;
	for (int i = 0; i < map.size(); i++) {
	    if (other.getbitMap().get(i) == true && map.get(i) == false) {
		return true;
	    }
	}
	return false;
    }
    
    synchronized public boolean chunkExists (int chunkNo) {
	return map.get(chunkNo);
    }
    
    synchronized public void setChunk (int chunkNo) {
	if (chunkExists(chunkNo))
	    return;
	
	map.set(chunkNo, true);
	chunksSet++;
    }
    
    public synchronized boolean isDone () {
	return (chunksSet == map.size());
    }
    
    public int getFirstMissingChunk (bitMap other) {
	for (int i = 0; i < map.size(); i++) {
	    if (!chunkExists(i) && other.chunkExists(i)) {
		return i;
	    }
	}
	return -1;
    }
    
    synchronized public int getRandomMissingChunk (bitMap other) {
	int size = (map.size()-1);
	int from = (int)(Math.ceil(Math.random()*size));
	int index = (from+1)%(size+1);
	while (index != from) {
	    if (!chunkExists(index) && other.chunkExists(index)) {
		return index;
	    }
	    index = (index + 1)%(size+1);
	}
	
	return -1;
    }
    
    synchronized ArrayList <Boolean> getbitMap () {
	return map;
    }
    
    synchronized public int getchunksSet () {
	return chunksSet;
    }
    
}


public class peerProcess {

    int pid;
    static int copypid;
    ServerSocket listeningSocket;
    int clients;
    static PrintWriter logger = null;
    boolean done;
    static int chunkSize;
    int numberOfchunks;
    boolean fileExists;
    int fileSize;
    static Hashtable<Integer, bitMap> peerbitmap;
    static Hashtable<Integer, Connection> connmap;
    static bitMap bitmap;
    
    static Hashtable <Integer, Integer> downloadList;
    static Vector <Integer> interestedList;
    static Vector <Integer> prefNbrs;
    Vector <Integer> unPrefNbrs;
    Vector <Integer> prevPrefNbrs;
    Vector <Integer> prevUnPrefNbrs;
    int ouNbr;
    
    int noPrefNbrs;
    BufferedReader bf;
    int UnchokingInterval;
    int OptimisticUnchokingInterval;
    ArrayList<Thread> connThreads;
    boolean isDone;
    String fileName;
    
    peerProcess() {

    }
    
    static void log (String string) {
	
	if (logger == null) {
	    try {
		logger = new PrintWriter(new BufferedWriter(new FileWriter("log_peer_" + copypid)));
	    } catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	    }
	}
	java.util.Date date= new java.util.Date();
	String logstring = new Timestamp(date.getTime()) + " " + string;
	logger.println(logstring);
    }
    
    int splitFile() {
	
	String line;
	try {
	    while ((line = bf.readLine())!= null) {
		String sub = (line.substring(0, line.indexOf(' ')));
		int peerid = Integer.parseInt(sub);
		line = line.substring(line.indexOf(' ')+1);
		line = line.substring(line.indexOf(' ')+1);
		line = line.substring(line.indexOf(' ')+1);
		if (peerid == pid) {
		    fileExists = (Integer.parseInt(line) == 1);
		    bf = new BufferedReader(new FileReader(new File (System.getProperty("user.dir") + "/PeerInfo.cfg")));
		    break;
		}
	    }
	} catch (NumberFormatException | IOException e1) {
	    // TODO Auto-generated catch block
	    e1.printStackTrace();
	}
	
	new File(System.getProperty("user.dir") + "/peer_" + pid + "/").mkdir();
	
	bitmap = new bitMap(fileExists, (int)Math.ceil((float)fileSize/chunkSize));
	if (!fileExists) {
	    return 0;
	}
	
	FileInputStream fis = null;
	byte [] buffer = new byte [chunkSize];
	
	try {
	    fis = new FileInputStream(new File(System.getProperty("user.dir") + "/" + fileName));
	} catch (FileNotFoundException e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	}
	 
	int index = 0;
	int bytesRead = 0;
	while (true) {
	    
	    OutputStream splitOut;
	    
	    try {
		bytesRead = fis.read(buffer, 0, chunkSize);
		
		if (bytesRead == -1) {
		    break;
		}
		    
		File file = new File(System.getProperty("user.dir") + "/peer_" + pid + "/chunk" + index++);
		splitOut = new FileOutputStream(file);
		splitOut.write(buffer);
		splitOut.close();
	    } catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	    }
	    
	    if (bytesRead < chunkSize) {
		break;
	    }
	
	}
	
	try {
	    fis.close();
	} catch (IOException e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	}
	return index;
    }
    
    void joinFile() {
	
	FileOutputStream fos = null;
	byte [] buffer = new byte [chunkSize];

	try {
	    fos = new FileOutputStream(new File(System.getProperty("user.dir") + "/peer_" + pid + "/" + fileName));
	     
	} catch (FileNotFoundException e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	}
	 
	int index = 0;
	int bytesRead = 0;
	while (index < numberOfchunks) {
	    
	    FileInputStream fis = null;
	    File file = null;
	    try {
		file = new File(System.getProperty("user.dir") + "/peer_" + pid + "/chunk" + index++);
		fis = new FileInputStream(file);
		if (!fileExists) 
		    bytesRead = fis.read(buffer, 0, chunkSize);
	    } catch (IOException e1) {
		// TODO Auto-generated catch block
		e1.printStackTrace();
	    }
	    if (bytesRead == -1) {
		//log ("breakin cause -1 ", pid);
		break;
	    }
		    
	    try {
		if (!fileExists)
		    fos.write(buffer);
		fis.close();
		file.delete();
	    } catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	    }

	}
	
	if (!fileExists)
	    log ("Peer " +  pid + " has downloaded the complete file");
	
	try {
	    fos.close();
	} catch (IOException e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	}
    }

    
    void ConnectToPeer (String hostname, int port, int peerId) throws IOException {
	
	Socket socket = null;
	try {
	    
	    if (connmap.containsKey(peerId)) {
		return;
	    } 
	    
	    socket = new Socket(hostname, port);
	    
	    log ("Peer " + pid + " makes a connection to Peer " + peerId);
	    Connection newConn = new Connection (socket, pid);
	    Thread ConnThread = new Thread (newConn);
	    ConnThread.start();
	    connmap.put(peerId, newConn);
	    connThreads.add(ConnThread);
	} catch (IOException e) {
	    // TODO Auto-generated catch block
	    //e.printStackTrace();
	}
	
    }
    
    void setupChokeUnchoke () {
	Timer timer = new Timer ();
	timer.scheduleAtFixedRate(new ChokeUnchoke (timer), 0, UnchokingInterval*1000);
	Timer timerOU = new Timer ();
	timerOU.scheduleAtFixedRate(new OptimisticChokeUnchoke (timerOU), 0, OptimisticUnchokingInterval*1000);
    }
    
    class ChokeUnchoke extends TimerTask {
	
	Timer timer;
	public ChokeUnchoke(Timer timer) {
	    // TODO Auto-generated constructor stub
	    this.timer = timer;
	}
	
	class Node implements Comparator<Node>{
	    Integer id;
	    Integer downloadRate;
	    
	    Node (int id, int rate) {
		this.id = id;
		this.downloadRate = rate;
	    }
	    Node () {}
	    @Override
	    public int compare(Node o1, Node o2) {
		// TODO Auto-generated method stub
		if (o1.downloadRate > o2.downloadRate)
		    return 1;
		if (o1.downloadRate < o2.downloadRate)
		    return -1;
		
		return 0;
	    }
	}
	
	void updatePrefNbrs () {
	    
	    synchronized (interestedList) {
		synchronized (downloadList) {
	    
		    Set<Integer> keySet = downloadList.keySet();
		    Vector<Node> ilist = new Vector<Node>();
		    
		    prevPrefNbrs = (Vector<Integer>) prefNbrs.clone();
		    prevUnPrefNbrs = (Vector<Integer>) unPrefNbrs.clone();
		    prefNbrs.clear();
		    unPrefNbrs.clear();
		    for (Integer integer : keySet) {
			if (interestedList.contains(integer)) {
			    ilist.add(new Node (integer, downloadList.get(integer)));
			} else if (prevPrefNbrs.contains(integer)) {
			    prevPrefNbrs.remove((Object)integer);
			}
		    }
		    
		    Collections.sort(ilist, new Node());
		    int i = 0;
		    
		    for (i = 0; i < noPrefNbrs && i < ilist.size(); i++) {
			prefNbrs.add(ilist.get(i).id);
		    }
		    
		    for ( ;i < interestedList.size() && i < ilist.size();i++) {
			unPrefNbrs.add(ilist.get(i).id);
		    }
		}
	    }
	}
	
	@Override
	public void run() {
	    // TODO Auto-generated method stub
	    if (isDone) {
		timer.cancel();
		return;
	    }
	    
	    updatePrefNbrs ();
	    boolean change = false;
	    synchronized (prefNbrs) {
		for (Integer element : prefNbrs) {
		    if ((prevPrefNbrs != null && prevPrefNbrs.contains(element)) || element == ouNbr ) {
			continue;
		    }
		    change = true;
		    Message.sendMsg(new UnChoke(), connmap.get(element).getOutputStream());
		}
	    }

	    if (change) {
		log ("Peer " +  pid + " has the preferred neihbors " + prefNbrs);
	    } 
	    
	    
	    for (Integer element : unPrefNbrs) {
		if((prevUnPrefNbrs != null && prevUnPrefNbrs.contains(element)) || element == ouNbr) {
		    continue;
		}
		//System.out.print(" " + element + "  ");
		Message.sendMsg(new Choke(), connmap.get(element).getOutputStream());
	    }
	    //System.out.println();
	}
	
    }
    
    class OptimisticChokeUnchoke extends TimerTask {
	
	Timer timer;
	public OptimisticChokeUnchoke(Timer timer) {
	    // TODO Auto-generated constructor stub
	    this.timer = timer;
	}
	
	@Override
	public void run() {
	    // TODO Auto-generated method stub
	    if (isDone) {
		timer.cancel();
		return;
	    }
	    
	    if (interestedList.size() == 0) {
		return;
	    }
	    
	    synchronized (interestedList) {
		int index = (int)(Math.random()*interestedList.size());
		if (ouNbr != 0 && interestedList.get(index) != ouNbr) {
		    //System.out.println("Optimistically Unchoke " + interestedList.get(index));
		    Message.sendMsg(new UnChoke(), connmap.get(interestedList.get(index)).getOutputStream());
		    
		    if (ouNbr != 0 && !prefNbrs.contains(ouNbr)) {
			//System.out.println("Optimistically Choke " + ouNbr);
			Message.sendMsg(new Choke(), connmap.get(ouNbr).getOutputStream());
		    }
		    ouNbr = interestedList.get(index);
		    log ("Peer " +  pid + " has the optimistically-unchoked neighbot " + ouNbr);
		}
	    }
	    
	}
	
    }

    void loadCommonCfg () {
	
	Properties prop = new Properties();
	FileInputStream fis = null;
	try {
	    fis = new FileInputStream(new File (System.getProperty("user.dir")) + "/Common.cfg");
	    prop.load(fis);
	} catch (IOException e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	}
	
	noPrefNbrs = Integer.parseInt(prop.getProperty("NumberOfPreferredNeighbors"));
	UnchokingInterval = Integer.parseInt(prop.getProperty("UnchokingInterval"));
	OptimisticUnchokingInterval = Integer.parseInt(prop.getProperty("OptimisticUnchokingInterval"));
	fileName = prop.getProperty("FileName");
	fileSize = Integer.parseInt(prop.getProperty("FileSize"));
	chunkSize = Integer.parseInt(prop.getProperty("PieceSize"));
	numberOfchunks = (int)Math.ceil((float)fileSize/chunkSize);
	
    }
    
    void setupConnections () {
	
	String line;
	connThreads = new ArrayList<Thread> ();
        try {
	    while ((line = bf.readLine())!= null) {
		
		String sub = (line.substring(0, line.indexOf(' ')));
		int peerid = Integer.parseInt(sub);
		line = line.substring(line.indexOf(' ')+1);
		
		String hostname = line.substring(0, line.indexOf(' '));
		line = line.substring(line.indexOf(' ')+1);
		
		sub = (line.substring(0, line.indexOf(' ')));
		int portNo = Integer.parseInt (sub);
		line = line.substring(line.indexOf(' ')+1);
		if (peerid == pid) {
		    startListening(portNo);
		} else {
		    ConnectToPeer(hostname, portNo, peerid);
		}
	    }
	} catch (NumberFormatException | IOException e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	}
    }
    peerProcess(int pid) throws IOException {
	
	//log ("Starting ...", pid);
	try {
	    bf = new BufferedReader(new FileReader(new File (System.getProperty("user.dir") + "/PeerInfo.cfg")));
	} catch (FileNotFoundException e) {
	    System.out.println ("File not exists exception");
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	    System.exit(0);
	}
	
	peerbitmap = new Hashtable<Integer, bitMap> ();
        interestedList = new Vector <Integer> ();
        downloadList = new Hashtable <Integer, Integer> (); 
        prefNbrs = new Vector<Integer>();
        unPrefNbrs = new Vector<Integer>();
        isDone = false;
        
        loadCommonCfg ();
        /*noPrefNbrs = 2;
        chunkSize = 2000;
        UnchokingInterval = 5;
        OptimisticUnchokingInterval = 15;
        numberOfchunks = 57;*/
	this.pid = pid;
	copypid = pid;
    
        connmap = new Hashtable<Integer, Connection> ();
        
	splitFile();
        setupConnections ();
        setupChokeUnchoke ();    
       
    }
    
    class Listen implements Runnable {
	@Override
	public void run() {
	    // TODO Auto-generated method stub
	    boolean listen = true;
	    while (listen) {
		try {
		    Socket socket = listeningSocket.accept();
		    Thread ConnThread = new Thread (new Connection (socket, pid));
		    ConnThread.start();
		    connThreads.add(ConnThread);
		    
		} catch (SocketException e) {
		    // TODO Auto-generated catch block
		    listen = false;
		} catch (Exception e) {
		    System.err.println("Random exception");
		}
	    }
	}
    }
    
    void startListening(int port) {
	
	try {
	    listeningSocket = new ServerSocket(port, 0, InetAddress.getLocalHost());
	    
	    Thread listeningThread = new Thread(new Listen());
	    listeningThread.start();
	    
	} catch (IOException e) {
	    System.out.println("Unable to create socket ");
	    // TODO Auto-generated catc
	    e.printStackTrace();
	}
    }
    
    
    
    public static void main (String[] args) throws NumberFormatException, IOException {
	
	peerProcess myProcess = new peerProcess(Integer.parseInt(args[0]));
	
	try {
	    Thread.currentThread().sleep (1000*10);
	} catch (InterruptedException e) {
	    e.printStackTrace();
	}
	for (int i = 0; i < myProcess.connThreads.size(); i++) {
	    try {
		myProcess.connThreads.get(i).join();
	    } catch (InterruptedException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	    }
	}
	
	myProcess.isDone = true;
	myProcess.listeningSocket.close();
	myProcess.joinFile();
	logger.close();
    }
    
}
