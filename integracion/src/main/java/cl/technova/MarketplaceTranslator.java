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

final class MarketplaceTranslator {
 static JsonObject marketplace(JsonObject source){
  var customer=source.getAsJsonObject("cliente");var address=source.getAsJsonObject("direccion");var products=new JsonArray();
  for(var e:source.getAsJsonArray("items")){var i=e.getAsJsonObject();var p=new JsonObject();p.addProperty("sku",(String)null);p.addProperty("name",val(i,"producto"));p.addProperty("quantity",decimal(i,"cantidad"));p.addProperty("unitPrice",decimal(i,"precioUnitario"));products.add(p);}
  var ship=source.getAsJsonObject("shipping");if(!Objects.equals(val(source,"id"),val(ship,"idVenta")))throw new IllegalArgumentException("Shipping order mismatch");
  var meta=new JsonObject();meta.add("original",source.deepCopy());return CanonicalModel.base(val(source,"id"),"marketplace",val(source,"fecha"),CanonicalModel.normalizedCustomer(val(customer,"nombre"),val(customer,"apellido"),val(customer,"rut"),null),CanonicalModel.normalizedAddress(val(address,"calle"),val(address,"numero"),val(address,"comuna"),val(address,"ciudad"),null),products,decimal(ship,"costoEnvio"),val(ship,"moneda"),meta);
 }

}
