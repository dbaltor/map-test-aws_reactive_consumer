
// Denis test-map
// server command line: java -jar build/libs/heatmap-1.0.0.jar <Lab: 1,2,both. Default: both> <Vehicles. Defaul: 10> <Vehicles real refresh interval in sec. Default: 60> 
// example: java -jar build/libs/heatmap-1.0.0.jar both 10 2
// client command line: localhost:8080

package map;

import javax.annotation.PreDestroy;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Bean;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;
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
import java.util.*;
import java.util.regex.Pattern;
import java.io.IOException;

@EnableWebSocket
@SpringBootApplication
public class Application {
  
    private static final String SUPPLIER_TOPIC_ARN = "arn:aws:sns:eu-west-2:556385395922:heatmap";  
    private static final String HEATMAP_SUPPLIER_URL = "https://sqs.eu-west-2.amazonaws.com/556385395922/heatmap-supplier";      
    private static final String DLQ_ARN = "arn:aws:sqs:eu-west-2:556385395922:heatmap-dlq";
    private static final String DEFAULT_LOCATION_FILE = "./files/realtimelocation.csv";
    private static String[] args;
    private static String lab;
    private static String subscriptionQueueURL;
    private static String subscriptionArn;

    // Lambda used as callback when reading messages asynchronously. Static definition allows for self-reference.
    private static BiConsumer<List<Message>, Throwable> consumeHeatmap;
    
    // Flow of heatmap coordinates from the supplier
    private static ConnectableFlux<String> heatMapEvents;

    // Collections of clients' subscriptions
    private static final Map<String, Disposable> clients = new ConcurrentHashMap<>();
    
    public static void main(String[] args)
    {
      SpringApplication.run(Application.class, args);
      
      subscribeSupplier();
    }
    
    @Bean
    public CommandLineRunner commandLineRunner(ApplicationContext ctx) 
    {
        return args -> {
          
          final String DEFAULT_LAB = "both";
    
          this.args = args;
          lab = ((args.length > 0) ? args[0] : DEFAULT_LAB);
          switch(lab) {
            case "1":
              System.out.println("Lab 1 selected");
              break;
            case "2":
              System.out.println("Lab 2 selected");
              break;
            default:
              lab = DEFAULT_LAB; 
              System.out.println("Labs 1 and 2 selected");
          }
        };
    }
    
    // Subscribe the supplier topic
    private static void subscribeSupplier()
    {
      // Create Subscription queue for this consumer
      final String SUBSCRIPTION_QUEUE_NAME = "heatmp-queue-" + System.currentTimeMillis();
      
      try{
        subscriptionQueueURL = QueueManager.createQueue(SUBSCRIPTION_QUEUE_NAME, DLQ_ARN, SUPPLIER_TOPIC_ARN);
        System.out.println("Created Consumer's subscription queue with URL: " + subscriptionQueueURL);
      }catch( Exception e) {
        System.out.println("Exception caught while trying creating the subscription queue...");
        e.printStackTrace();
        System.exit(1);
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
        System.exit(1);
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
        System.exit(1);
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
        .publishOn(Schedulers.parallel())
        .publish();
      heatMapEvents.connect();
    }
      
    // Register a shutdown hook with the JVM
    @PreDestroy
    public static void unsubscribeSupplier()
    {
      // Remove the subscription and queue
      System.out.println("TEAR DOWN!!!!!!");
      
      // Build the SNS client
      SnsClient snsClient = SnsClient.builder()
        .build();     
      // Unsubscribe the Supplier's topic
      try{      
        snsClient.unsubscribe(UnsubscribeRequest.builder()
                              .subscriptionArn(subscriptionArn)
                              .build());
        System.out.println("Subscription deleted... " + subscriptionArn);
      }catch( Exception e) {
        System.out.println("Exception caught while trying to unsubscribe the topic...");
        e.printStackTrace();
        System.exit(1);
      }
      // Delete Subscription queue for this consumer
      try{
        QueueManager.deleteQueue(subscriptionQueueURL);
        System.out.println("Deleted queue: " + subscriptionQueueURL);
      }catch( Exception e) {
        System.out.println("Exception caught while trying deleting the subscription queue...");
        e.printStackTrace();
        System.exit(1);
      }
    }    
    
    @Component
    public static class MyWebSocketConfigurer implements WebSocketConfigurer 
    {
        @Override
        public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
            registry.addHandler(new MyBinaryHandler(), "/lab");
        }
    }
    
    @Component
    public static class MyBinaryHandler extends BinaryWebSocketHandler 
    {
      public void afterConnectionEstablished(WebSocketSession session)
      {
        // Send map access key
        System.out.println("Trying to send the map access key (MAP_KEY): " + System.getenv("MAP_KEY"));
        if (!WsPacket.send(session, "m0," + System.getenv("MAP_KEY"))){
          System.out.println("Error whilst trying to send map access key. Socket " + session.getId() + " closed!");
          return; 
        }
        if (lab.equals("1") || lab.equals("both")) {
          try {
            // Send start command to supplier
            QueueManager.put(HEATMAP_SUPPLIER_URL, "start");
            //***************** DEBUG
            System.out.println("NEW CLIENT CONNECTED!!! " + session.getId());
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
                  clients.get(session.getId()).dispose();
                  // remove client from the list of clients
                  clients.remove(session.getId());
                  //***************** DEBUG
                  System.out.println("SUBSCRIPTION REMOVED FROM THE LIST OF CLIENTS...");
                  System.out.println("Lab1: Total number of clients: " + clients.size());
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
              clients.remove(session.getId());
              //***************** DEBUG
              System.out.println("SUBSCRIPTION REMOVED FROM THE LIST OF CLIENTS...");
              System.out.println("Lab1: Total number of clients: " + clients.size());
              //*****************                
            });
            // Register this client's subscription
            clients.put(session.getId(), subscription);
            //***************** DEBUG
            System.out.println("SUBSCRIPTION ADDED TO THE LIST OF CLIENTS...");
            System.out.println("Lab1: Total number of clients: " + clients.size());
            //*****************                      
          }
          catch(Exception e) {
            System.out.println("Exception caught while trying to write into the queue: " + HEATMAP_SUPPLIER_URL);
            e.printStackTrace();
          }          
        }
        if (lab.equals("2") || lab.equals("both")) {
          new Lab2(session, args, DEFAULT_LOCATION_FILE);
        }  
      }
    }
}