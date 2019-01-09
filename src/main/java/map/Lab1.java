package map;

import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.CloseStatus;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sns.model.ConfirmSubscriptionRequest;
import software.amazon.awssdk.services.sns.model.UnsubscribeRequest;
import software.amazon.awssdk.services.sqs.model.Message;

import qm.QueueManager;

import reactor.core.publisher.Flux;
import reactor.core.publisher.ConnectableFlux;
import reactor.core.scheduler.Schedulers;
import reactor.core.Disposable;

import static java.util.stream.Collectors.*;
import java.util.function.BiConsumer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.io.IOException;

public class Lab1 { // Singleton
  
  private static final String SUPPLIER_TOPIC_ARN = "arn:aws:sns:eu-west-2:556385395922:heatmap";  
  private static final String DLQ_ARN = "arn:aws:sqs:eu-west-2:556385395922:heatmap-dlq";
  private static final String HEATMAP_SUPPLIER_URL = "https://sqs.eu-west-2.amazonaws.com/556385395922/heatmap-supplier";      
  
  private static String subscriptionQueueURL;
  private static String subscriptionArn;
  
  // Lambda used as callback when reading messages asynchronously. Static definition allows for self-reference.
  private static BiConsumer<List<Message>, Throwable> consumeHeatmap;  
  
  // Flow of heatmap coordinates from the supplier
  private ConnectableFlux<String> heatMapEvents;  
  
  // Collections of clients' subscriptions
  private final Map<String, Disposable> CLIENTS = new ConcurrentHashMap<>();
  
  // private constructor
  private Lab1() {}
  
  private static Lab1 instance;
  
  /**
   * Instantiate singleton and create resources required
   * 
   * @throws Exception
   */  
  public static Lab1 getInstance() throws Exception{
    if (instance == null){
      synchronized (Lab1.class) {
        if (instance == null){
          instance = new Lab1();
          // Subscribe Supplier's topic
          instance.subscribeSupplier();
          // Register hook to clear up resources when exiting
          Runtime.getRuntime().addShutdownHook(new Thread(){
            public void run() { 
              System.out.println("TEAR DOWN!!!!!!");
              try {
                instance.unsubscribeSupplier();
              }catch(Exception e) {
                System.out.println("Exception caught while trying unsubscribe Heatmap events producer...");
                e.printStackTrace();
              }              
            } 
          });           
        }
      }
    }
    return instance;    
  }
  
   /**
    * Subscribe this application to the Supplier's topic
    * 
    * @throws Exception
    */    
    private void subscribeSupplier() throws Exception
    {
      // Create Subscription queue for this consumer
      final String SUBSCRIPTION_QUEUE_NAME = "heatmp-queue-" + System.currentTimeMillis();
      
      try{
        subscriptionQueueURL = QueueManager.createQueue(SUBSCRIPTION_QUEUE_NAME, DLQ_ARN, SUPPLIER_TOPIC_ARN);
        System.out.println("Created Consumer's subscription queue with URL: " + subscriptionQueueURL);
      }catch( Exception e) {
        System.out.println("Exception caught while trying creating the subscription queue...");
        e.printStackTrace();
        throw e;
      }
      // Build the SNS client
      SnsClient snsClient = SnsClient.builder()
        .build();     
      // Subscribe the Supplier's topic
      try{      
        subscriptionArn = snsClient.subscribe(SubscribeRequest.builder()
                                                .topicArn(SUPPLIER_TOPIC_ARN)
                                                .protocol("sqs")
                                                .endpoint(QueueManager.getQueueARN(subscriptionQueueURL))
                                                .attributes(new HashMap<String,String>(){{
                                                    put("RawMessageDelivery", "true");}})
                                                .build())
          .subscriptionArn();
        System.out.println("Subscription created... " + subscriptionArn);
      }catch( Exception e) {
        System.out.println("Exception caught while trying to subscribe the topic...");
        e.printStackTrace();
        throw e;
      }
      // Confirm subscription when subscribing another account's queue
      /*try{    
        String confirSubscription = snsClient.confirmSubscription(ConfirmSubscriptionRequest.builder()
                                                                    .topicArn(SUPPLIER_TOPIC_ARN)
                                                                    .token(subscriptionArn)
                                                                    .build())
          .subscriptionArn();
        System.out.println("Subscription confirmed... " + confirSubscription);
      }catch( Exception e) {
        System.out.println("Exception caught while trying to confirm the subscription...");
        e.printStackTrace();
        return false;
      } */
      
      // Bridge between AWS asynchronous API and Reactive subscribers
      final Flux<String> bridge = Flux.create(sink -> {
        // Callback to flow the coordinates message to subscribers
        consumeHeatmap = (msgs, err) -> {
          if (msgs != null) {
            // Get messages received and stream each line individually 
            msgs.stream()
              .map(Message::body)
              .flatMap(Pattern.compile("\n")::splitAsStream)
              //***************** DEBUG      
              //.peek(System.out::println)
              //*****************
              .forEach(sink::next); // Flow coordinates to subscribed consumers
            
            // Register again the callback
            QueueManager.getAsync(subscriptionQueueURL)
              .whenComplete(consumeHeatmap);
          }
          else if (err != null) {
            // Print error on the console and signal the subscribers
            err.printStackTrace();
            sink.error(err);
          }
        };
        // Asunchronously poll the queue for coordinates registering a callback
        QueueManager.getAsync(subscriptionQueueURL)
          .whenComplete(consumeHeatmap);
      });
      // Set up the Flow and start emitting heatmap events
      heatMapEvents = bridge
        //***************** DEBUG      
        //.log()
        //*****************
        .subscribeOn(Schedulers.parallel())
        .publish();
      heatMapEvents.connect();
    }

   /**
    * Clear up this application's queue and subscription to the Supplier
    * 
    * @throws Exception
    */    
    private void unsubscribeSupplier() throws Exception
    {
      // Build the SNS client
      SnsClient snsClient = SnsClient.builder()
        .build();     
      // Unsubscribe the Supplier's topic
      try{      
        snsClient.unsubscribe(UnsubscribeRequest.builder()
                              .subscriptionArn(subscriptionArn)
                              .build());
        System.out.println("Subscription deleted... " + subscriptionArn);
      }catch(Exception e) {
        System.out.println("Exception caught while trying to unsubscribe the topic...");
        e.printStackTrace();
        throw e;
      }
      // Delete Subscription queue for this consumer
      try{
        QueueManager.deleteQueue(subscriptionQueueURL);
        System.out.println("Deleted queue: " + subscriptionQueueURL);
      }catch(Exception e) {
        System.out.println("Exception caught while trying deleting the subscription queue...");
        e.printStackTrace();
        throw e;
      }
    }
          
   /**
    * Subscribe the given client to receive heatmap events
    * 
    * @param session Client's WebSocket
    */    
    public void subscribeClient(WebSocketSession session) {
      try {
        // Send start command to supplier
        QueueManager.put(HEATMAP_SUPPLIER_URL, "start");
        //***************** DEBUG
        System.out.println("LAB 1 - NEW CLIENT CONNECTED!!! " + session.getId());
        //*****************
        // subscribing for Heat Map events
        Disposable subscription = heatMapEvents
          //***************** DEBUG
          //.log()
          //*****************
          .subscribe(event -> {
          //***************** DEBUG
          //System.out.println("Sending: " + event);
          //*****************              
          if (!WsPacket.send(session, "m1," + event)){
            // Client socket is closed. 
            System.out.println("Lab 1 finished. Socket " + session.getId() + " closed!");
            // Unsubscribe the Flux to stopping receiving events
            CLIENTS.get(session.getId()).dispose();
            // remove client from the list of clients
            CLIENTS.remove(session.getId());
            //***************** DEBUG
            System.out.println("SUBSCRIPTION REMOVED FROM THE LIST OF CLIENTS...");
            System.out.println("Lab1: Total number of clients: " + CLIENTS.size());
            //*****************                  
          }              
        }, error -> {
          // Error received from the Flux! 
          System.out.println("Lab 1 finished. Error received from the Flux! Will close socket " + session.getId());  
          try {
            // Disconnect client!
            session.close(CloseStatus.SERVER_ERROR);
          }
          catch(IOException e) {
            System.out.println("Exception caught while trying to close the socket " + session.getId());
            e.printStackTrace();
          }
          // remove client from the list of clients
          CLIENTS.remove(session.getId());
          //***************** DEBUG
          System.out.println("SUBSCRIPTION REMOVED FROM THE LIST OF CLIENTS...");
          System.out.println("Lab1: Total number of clients: " + CLIENTS.size());
          //*****************                
        });
        // Register this client's subscription
        CLIENTS.put(session.getId(), subscription);
        //***************** DEBUG
        System.out.println("SUBSCRIPTION ADDED TO THE LIST OF CLIENTS...");
        System.out.println("Lab1: Total number of clients: " + CLIENTS.size());
        //*****************                      
      }
      catch(Exception e) {
        System.out.println("Exception caught while trying to write into the queue: " + HEATMAP_SUPPLIER_URL);
        e.printStackTrace();
      }      
    }
}
