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

final class MarketplaceAdapter {
 static int run() throws Exception {
  try(var f=factory()){return run(env("MARKETPLACE_URL","http://localhost:8091"),f);}
 }
 static int run(String url, ActiveMQConnectionFactory f) throws Exception {
  var orders=JsonParser.parseString(get(url+"/orders/today")).getAsJsonArray();int successful=0;List<String> failed=new ArrayList<>();int position=0;
  try(var c=f.createConnection();var s=c.createSession(Session.SESSION_TRANSACTED)) {
   c.start();var producer=s.createProducer(s.createQueue("eah_mkp_pedidos"));
   for(var e:orders){position++;String id="<missing-id-"+position+">";try {
    var o=e.getAsJsonObject().deepCopy();String found=val(o,"id");if(found==null||found.isBlank())throw new IllegalArgumentException("Missing id");id=found;
    var shipping=object(get(url+"/orders/"+id+"/shipping-cost"));if(!id.equals(val(shipping,"idVenta")))throw new IllegalArgumentException("Shipping order mismatch");o.add("shipping",shipping);
    var message=s.createTextMessage(G.toJson(o));message.setStringProperty("_AMQ_DUPL_ID","marketplace:"+id);message.setStringProperty("orderId",id);producer.send(message);s.commit();successful++;System.out.println("Published enriched order "+id+" "+G.toJson(o));
   } catch(Exception ex){s.rollback();failed.add(id);System.err.println("Order failed "+id+": "+ex);}}
  }
  System.out.println("Orders found="+orders.size()+" successful="+successful+" failed="+failed.size()+" failedIds="+failed);return failed.isEmpty()?0:1;
 }
}
