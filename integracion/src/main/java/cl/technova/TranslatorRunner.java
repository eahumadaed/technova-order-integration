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

final class TranslatorRunner {
 static void translate(boolean isWeb)throws Exception{
  try(var f=factory();var c=f.createConnection();var s=c.createSession(Session.SESSION_TRANSACTED)){var in=s.createQueue(isWeb?"eah_web_pedidos":"eah_mkp_pedidos");var consumer=s.createConsumer(in);var producer=s.createProducer(s.createTopic("eah_pedidos"));c.start();while(true){Message m=consumer.receive();try{String payload=((TextMessage)m).getText();String id=m.getStringProperty("orderId");if(isWeb&&(id==null||id.isBlank()))throw new IllegalArgumentException("Missing orderId");var o=isWeb?WebTranslator.web(payload,id):MarketplaceTranslator.marketplace(object(payload));var out=s.createTextMessage(G.toJson(o));out.setStringProperty("orderId",val(o,"id"));producer.send(out);s.commit();System.out.println("Translated order "+val(o,"id")+" to eah_pedidos "+G.toJson(o));}catch(Exception ex){s.rollback();System.err.println("Translation failed: "+ex);Thread.sleep(2000);}}}
 }

}
