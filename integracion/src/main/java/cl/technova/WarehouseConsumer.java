package cl.technova;
import com.google.gson.*;
import jakarta.jms.*;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import java.io.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static cl.technova.Shared.*;
import static cl.technova.MessagingConfiguration.*;

final class WarehouseConsumer {
 static void consume() throws Exception {
  try(var f=factory();var c=f.createConnection();var s=c.createSession(Session.SESSION_TRANSACTED)) {
   var consumer=s.createConsumer(s.createQueue("eah_pedidos::eah_bodega"));c.start();
   while(true){Message message=consumer.receive();try{var order=object(((TextMessage)message).getText());String id=val(order,"id");if(id==null||!id.matches("[A-Za-z0-9_-]+"))throw new IllegalArgumentException("Invalid id");System.out.println("Warehouse order "+id+" products="+order.get("products"));s.commit();}catch(Exception ex){s.rollback();System.err.println("Consumer failed: "+ex);Thread.sleep(2000);}}
  }
 }
}
