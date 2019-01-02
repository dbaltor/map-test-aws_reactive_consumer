package map;

import java.util.Scanner;
import java.io.InputStream;
import org.springframework.web.socket.WebSocketSession;

class Lab1 extends Thread {
   InputStream inputStream;
   WebSocketSession session;
   
   public Lab1(WebSocketSession s, InputStream is) {
     super();
     session = s;
     inputStream = is;
     this.start();
   }
   
   public void run() {
     Scanner scan = new Scanner(inputStream);
     String line; 
     while(true){
       line = scan.nextLine();  
       if (line.equals("")){
         continue;
       }
       if (!WsPacket.send(session, "m1," + line)){
         System.out.println("Lab 1 finished. Socket " + session.getId() + " closed!");
         return; 
       }
     }
   }
}
