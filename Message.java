import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;

abstract public class Message implements Serializable {
    int length;
    char type;
    Object payload;
    
    static void sendMsg (Message msg, OutputStream out) {
	
	ObjectOutputStream oos;
	synchronized (out) {
	    try {
		oos = new ObjectOutputStream(out);
		oos.writeObject(msg);
	    } catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	    }
	}
    }
    
    static Message rcvMsg (InputStream in) throws IOException {
	
	ObjectInputStream ois;
	try {
	    ois = new ObjectInputStream(in);
	    return (Message) ois.readObject();
	/*} catch (IOException e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();*/
	} catch (ClassNotFoundException e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	}
	return null;
    }
}

class bitField extends Message implements Serializable {
    
    bitField (bitMap bitmap) {
	length = bitmap.getbitMap().size();
	type = 5;
	payload = bitmap.getbitMap();
    }
}

class Interested extends Message implements Serializable {
    
    Interested () {
	length = 0;
	type = 2;
	payload = null;
    }
}

class NotInterested extends Message implements Serializable {
    
    NotInterested () {
	length = 0;
	type = 3;
	payload = null;
    }
}

class Choke extends Message implements Serializable {
    
    Choke () {
	length = 0;
	type = 0;
	payload = null;
    }
}

class UnChoke extends Message implements Serializable {
    
    UnChoke () {
	length = 0;
	type = 1;
	payload = null;
    }
}

class Request extends Message implements Serializable {
    
    Request (int chunkNo) {
	length = 4;
	type = 6;
	payload = chunkNo;
    }
}

class Piece extends Message implements Serializable {
    class Node implements Serializable {
	int chunkNo;
	Object data;
	Node (int chunkNo, Object data) {
	    this.chunkNo = chunkNo;
	    this.data = data;
	}
    }
    
    Piece (int lengthData, Object data, int chunkNo) {
	length = lengthData + 4;
	type = 7;
	payload = new Node (chunkNo, data);
    }
    
    int getChunkNo () {
	return ((Node)payload).chunkNo;
    }
    
    Object getData () {
	return ((Node)payload).data;
    }
    
}

class Have extends Message implements Serializable {
    
    Have (int chunkNo) {
	length = 4;
	type = 4;
	payload = chunkNo;
    }
}

