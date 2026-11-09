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

final class CanonicalModel {
 static JsonObject base(String id,String source,String date,JsonObject customer,JsonObject address,JsonArray products,BigDecimal shipping,String currency,JsonObject metadata){
  if(id==null||id.isBlank()||date==null||products.isEmpty())throw new IllegalArgumentException("Missing order fields");
  BigDecimal subtotal=BigDecimal.ZERO; for(JsonElement e:products){var p=e.getAsJsonObject(); var q=decimal(p,"quantity"); var price=decimal(p,"unitPrice"); if(q.signum()<=0||price.signum()<0)throw new IllegalArgumentException("Invalid amount"); BigDecimal line=price.multiply(q); p.addProperty("subtotal",line); subtotal=subtotal.add(line);}
  if(shipping!=null&&shipping.signum()<0)throw new IllegalArgumentException("Invalid shipping");
  var o=new JsonObject();o.addProperty("id",id);o.addProperty("originalId",source.equals("marketplace")?id:null);o.addProperty("source",source);o.addProperty("purchaseDate",date);o.add("customer",customer);o.add("shippingAddress",address);o.add("products",products);o.addProperty("subtotal",subtotal); if(shipping==null)o.add("shippingCost",JsonNull.INSTANCE);else o.addProperty("shippingCost",shipping); o.addProperty("currency",currency);if(shipping==null)o.addProperty("total",subtotal);else o.addProperty("total",subtotal.add(shipping));o.add("metadata",metadata);return o;
 }
 static JsonObject normalizedCustomer(String first,String last,String rut,String phone){var c=new JsonObject();c.addProperty("name",first);c.addProperty("lastName",last);c.addProperty("rut",rut);c.addProperty("phone",phone);return c;}
 static JsonObject normalizedAddress(String street,String number,String district,String city,String full){var a=new JsonObject();a.addProperty("street",street);a.addProperty("number",number);a.addProperty("district",district);a.addProperty("city",city);a.addProperty("streetAndNumber",full);return a;}

}
