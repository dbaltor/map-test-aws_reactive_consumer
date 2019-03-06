package map;

import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.CloseStatus;

import reactor.core.publisher.Flux;
import reactor.core.publisher.ConnectableFlux;
import reactor.core.scheduler.Schedulers;
import reactor.core.Disposable;

import static java.time.Duration.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import java.io.IOException;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.time.LocalTime;

public class Lab2 { // Singleton
  
  private static final String DEFAULT_LOCATION_FILE = "./files/realtimelocation.csv"; 
  
  // Flow of vehicle location coordinates loaded from the file
  private ConnectableFlux<String> vehicleLocations;  
  
  // Collection of clients' subscriptions
  private final Map<String, Disposable> SUBSCRIPTIONS = new ConcurrentHashMap<>();  
  // Collection of clients' window delimiter used to control the flow of locations  
  private final Map<String, String> TIME_DELIMITER = new ConcurrentHashMap<>();  
  
  // private constructor
  private Lab2() {}
  private static Lab2 instance;

  private static final int DEFAULT_MAX_VEHICLES = 10;
  private static final int DEFAULT_REFRESH_RATE = 60;
  private int maxVehicles;
  private int refreshRate;
  private WebSocketSession session;
  private String filename;
  
  /**
   * Instantiate singleton and open required resources
   * 
   * @throws Exception
   */  
  public static Lab2 getInstance(String[] args) throws IOException{
    if (instance == null){
      synchronized (Lab2.class) {
        if (instance == null){
          instance = new Lab2();
          
          // Number of vehicles to track
          try { 
            instance.maxVehicles = (args.length > 1) ? Integer.parseInt(args[1]) : DEFAULT_MAX_VEHICLES; 
          }  
          catch (NumberFormatException e) { 
            instance.maxVehicles = DEFAULT_MAX_VEHICLES;
            System.out.println("'" + args[1] + "' is not a valid integer number! Using the default value: " + DEFAULT_MAX_VEHICLES); 
          } 
          System.out.println("Number of vehicles to track = " + instance.maxVehicles);
          
          // Refresh rate
          try { 
            instance.refreshRate = (args.length > 2) ? Integer.parseInt(args[2]) : DEFAULT_REFRESH_RATE; 
          }  
          catch (NumberFormatException e) { 
            instance.refreshRate = DEFAULT_REFRESH_RATE;
            System.out.println("'" + args[2] + "' is not a valid integer number! Using the default value: " + DEFAULT_REFRESH_RATE); 
          } 
          System.out.println("Vehicles real refresh rate = " + instance.refreshRate);
          
          // Filename
          instance.filename = (args.length > 3) ? args[3] : DEFAULT_LOCATION_FILE;
          System.out.println("Reading vehicles' locations from the file: " + instance.filename);                
          
          // Open locations file and create a flux from it
          instance.openLocationsFile();
        }
      }
    }
    return instance;    
  }
  
   /**
    * Instantiate a flux of vehicle's locations from the vehicles location file
    * 
    * @throws Exception
    */    
    private void openLocationsFile() throws IOException
    {
      //List of first N vehicles to track to
      List<String> vehicles = new ArrayList<>(maxVehicles);
      
      // Set up the flow to be loaded from the file
      vehicleLocations = Flux.fromStream(Files.lines(Paths.get(filename)))
        // Filter out empty lines
        .map(line -> line.trim())
        .filter(line -> !line.isEmpty())
        // initialise the list of vehicles to track to
        .doOnNext(line -> {
          String vehicle = line.split(",")[1];
          if (vehicles.size() < maxVehicles && !vehicles.contains(vehicle))
            vehicles.add(vehicle);
          })
        // Filter out the rest of the vechicles
        .filter(line -> {
          String vehicle = line.split(",")[1];
          return vehicles.contains(vehicle) ? true : false;
        })
        .subscribeOn(Schedulers.parallel())  
        //***************** DEBUG      
        //.log()
        //*****************
        // keep the content in memory to be replayed later on 
        .replay(); 
      // Start reading the locations
      vehicleLocations.connect();        
    }

         
   /**
    * Subscribe the given client to receive vehicle locations
    * 
    * @param session Client's WebSocket
    */    
    public void subscribeClient(WebSocketSession session) {
      // Register this client's initial time window delimiter
      TIME_DELIMITER.put(session.getId(), "");        
      
      //***************** DEBUG
      System.out.println("LAB2 - NEW CLIENT CONNECTED!!! " + session.getId());
      //*****************
      // subscribing for vechicle locations
      Disposable subscription = vehicleLocations
        .windowUntil(line -> {
          String time = line.split(",")[0];
          if (TIME_DELIMITER.get(session.getId()) == "") {
            // First element of this window
            TIME_DELIMITER.put(session.getId(), time);
            return false;
          }
          else if (LocalTime.parse(time).isBefore(LocalTime.parse(TIME_DELIMITER.get(session.getId())).plusMinutes(refreshRate))) { 
            // Element of this window
            return false;
          }
          else {
            // Fist element of the next window
            TIME_DELIMITER.put(session.getId(), time);
            return true;
          }
        }, true) // cutBefore = true. Boundary element belongs to the next window
        // Apply the refreshRate delaying each window 
        .delayElements(ofSeconds(refreshRate))
        .flatMap(windowFlow -> windowFlow
                   // Count elements to filter out all elments but the first locations of each vehicle in this window
                   .index()
                   .handle((tuple, sink) -> {
                     if (tuple.getT1() < maxVehicles){
                       //***************** DEBUG
                       //System.out.println("Line: " + tuple.getT2());
                       //*****************
                       // Format each message
                       String[] msg = tuple.getT2().split(",");
                       String vehicle = msg[1];
                       String lat = msg[2];
                       String longi = msg[3];
                       sink.next("m2," + vehicle + "," + lat + "," + longi);
                     }
                   }))
        //***************** DEBUG
        //.log()
        //*****************        
        .subscribe(msg -> {
          //***************** DEBUG
          //System.out.println("Sending: " + msg);
          //*****************              
          if (!WsPacket.send(session, (String)msg)){
            // Client socket is closed. 
            System.out.println("Lab 2 finished. Socket " + session.getId() + " closed!");
            // Unsubscribe the Flux to stopping receiving msgs
            SUBSCRIPTIONS.get(session.getId()).dispose();
            // remove client from the list of clients
            SUBSCRIPTIONS.remove(session.getId());
            TIME_DELIMITER.remove(session.getId());
            //***************** DEBUG
            System.out.println("SUBSCRIPTION REMOVED FROM THE LIST OF SUBSCRIPTIONS...");
            System.out.println("Lab2: Total number of clients: " + SUBSCRIPTIONS.size());
            //*****************                  
          }              
        }, error -> {
          // Error received from the Flux! 
          System.out.println("Lab 2 finished. Error received from the Flux! Will close socket " + session.getId());  
          try {
            // Disconnect client!
            session.close(CloseStatus.SERVER_ERROR);
          }
          catch(IOException e) {
            System.out.println("Exception caught while trying to close the socket " + session.getId());
            e.printStackTrace();
          }
          // remove client from the list of clients
          SUBSCRIPTIONS.remove(session.getId());
          TIME_DELIMITER.remove(session.getId());         
          //***************** DEBUG
          System.out.println("SUBSCRIPTION REMOVED FROM THE LIST OF SUBSCRIPTIONS...");
          System.out.println("Lab2: Total number of clients: " + SUBSCRIPTIONS.size());
          //*****************                
        });
      
      // Register this client's subscription
      SUBSCRIPTIONS.put(session.getId(), subscription);
      //***************** DEBUG
      System.out.println("SUBSCRIPTION ADDED TO THE LIST OF SUBSCRIPTIONS...");
      System.out.println("Lab2: Total number of clients: " + SUBSCRIPTIONS.size());
      //*****************                      
    }
}
