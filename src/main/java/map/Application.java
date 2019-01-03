
// Denis test-map AWS reactive consumer
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

@EnableWebSocket
@SpringBootApplication
public class Application {
  
    private static final String DEFAULT_LOCATION_FILE = "./files/realtimelocation.csv";
    private static String[] args;
    private static String lab;
    
    public static void main(String[] args)
    {
      SpringApplication.run(Application.class, args);
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
            Lab1.getInstance().subscribeClient(session);
          } catch(Exception e) {
            // Exception caught whilst trying to create Lab1 singleton class. Application must exit!
            System.exit(1);
          }
        }
        if (lab.equals("2") || lab.equals("both")) {
          new Lab2(session, args, DEFAULT_LOCATION_FILE);
        }  
      }
    }
}