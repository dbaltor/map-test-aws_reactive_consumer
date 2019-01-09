
package map;

import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.BinaryMessage;
import java.io.IOException;

public class WsPacket {
  
  public static synchronized boolean send(WebSocketSession session, String msg){

    if (!session.isOpen()) {
      return false; 
    }
    try {
      session.sendMessage(new BinaryMessage(msg.getBytes())); 
      //System.out.println("Sent: " + msg);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return true;
  }
}