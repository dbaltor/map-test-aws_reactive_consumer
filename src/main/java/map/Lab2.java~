package map;

import java.util.Arrays;
import java.io.*;
import java.time.LocalTime;
import java.util.concurrent.TimeUnit;
import org.springframework.web.socket.WebSocketSession;

class Lab2 extends Thread {
   final int DEFAULT_MAX_VEHICLES = 10;
   final int DEFAULT_REFRESH_RATE = 60;
   int maxVehicles;
   int refreshRate;
   WebSocketSession session;
   String filename;
   
   public Lab2(WebSocketSession s, String[] args, String pfn) {
     super();
     session = s;
     filename = pfn;
     
     // Number of vehicles to track
     try { 
       maxVehicles = (args.length > 1) ? Integer.parseInt(args[1]) : DEFAULT_MAX_VEHICLES; 
     }  
     catch (NumberFormatException e) { 
       maxVehicles = DEFAULT_MAX_VEHICLES;
       System.out.println("'" + args[1] + "' is not a valid integer number! Using the default value: " + DEFAULT_MAX_VEHICLES); 
     } 
     System.out.println("Number of vehicles to track = " + maxVehicles);
            
     // Refresh rate
     try { 
       refreshRate = (args.length > 2) ? Integer.parseInt(args[2]) : DEFAULT_REFRESH_RATE; 
     }  
     catch (NumberFormatException e) { 
       refreshRate = DEFAULT_REFRESH_RATE;
       System.out.println("'" + args[2] + "' is not a valid integer number! Using the default value: " + DEFAULT_REFRESH_RATE); 
     } 
     System.out.println("Vehicles real refresh rate = " + refreshRate);
          
     // start a thread for this lab
     this.start();
   }
   
   public void run() {
     
     String[] vehicles = new String[maxVehicles];
     int stored_vehicles = 0;
     int vehicles_to_send = maxVehicles;
     String last_time = "";     
     
     try( BufferedReader br = new BufferedReader(new FileReader(filename)) ) {
       for(String line; (line = br.readLine()) != null; ) {
         if (line.equals("")){
           continue;
         }
         String[] msg = line.split(",");
         String time = msg[0];
         String vehicle = msg[1];
         String lat = msg[2];
         String longi = msg[3];
         String msg2send = "m2," + vehicle + "," + lat + "," + longi;
         
         // add current vehicle to the to send list
         if (stored_vehicles < maxVehicles && !Arrays.asList(vehicles).contains(vehicle)) {
           vehicles[stored_vehicles++] = vehicle;
         }

         // PICK ONE VEHICLE UP
         /*   if (vehicle.equals("Vehicle_847")) {
           vehicles[stored_vehicles++] = vehicle;
         }*/
   
         //  vehicle listed to send
         if (Arrays.asList(vehicles).contains(vehicle)) {
           // MAX_VEHICLES not exceeded 
           if (vehicles_to_send > 0) {
             // send message
             if (!WsPacket.send(session, msg2send)) {
               return; // socket closed!
             }
             // decrement vehicles to send
             vehicles_to_send--;
             // record the time
             last_time = time;
           } else if ( LocalTime.parse(time).isAfter(LocalTime.parse(last_time).plusMinutes(refreshRate))) {
             // maxVehicles exceeded AND
             // the record happened at least 1 minute after the last record sent. 
             // (Allows for skiping data regarding the vehicle being monitored if the interval chosen is longer than the reads on the file)

             // pause reading lines...
             try{
               TimeUnit.SECONDS.sleep(refreshRate);
             }
             catch(InterruptedException ie){}; 
             
             // reset counter
             vehicles_to_send = maxVehicles;      
             // send message
             if (!WsPacket.send(session, msg2send)){
               System.out.println("Lab 2 finished. Socket " + session.getId() + " closed!");
               return;
             }
             //decrement vehicles to send
             vehicles_to_send--;
           }
         }
       }
     }
     catch (IOException e) {
       System.out.println("IOException cought while reading the file: " + filename);
       System.out.println(e);
     }
   }
}
