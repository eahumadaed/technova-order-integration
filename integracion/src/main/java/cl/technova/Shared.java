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

final class Shared {
 static final Gson G = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
 static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
 static String get(String u)throws Exception{var r=HTTP.send(HttpRequest.newBuilder(URI.create(u)).timeout(Duration.ofSeconds(10)).GET().build(),HttpResponse.BodyHandlers.ofString()); if(r.statusCode()!=200)throw new IOException("HTTP "+r.statusCode()+" "+u); return r.body();}
 static JsonObject object(String s){return JsonParser.parseString(s).getAsJsonObject();}
 static String val(JsonObject o,String key){return o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsString():null;}
 static BigDecimal decimal(JsonObject o,String key){return o.get(key).getAsBigDecimal();}

}
