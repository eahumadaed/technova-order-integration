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

final class WebTranslator {
 static String child(Element e,String name){var n=e.getElementsByTagName(name);return n.getLength()==0?null:n.item(0).getTextContent();}
 static JsonObject web(String xml,String id)throws Exception{
  var f=DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setFeature("http://xml.org/sax/features/external-general-entities",false);f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);f.setXIncludeAware(false);f.setExpandEntityReferences(false);
  Element root=f.newDocumentBuilder().parse(new InputSource(new StringReader(xml))).getDocumentElement();
  Element c=(Element)root.getElementsByTagName("comprador").item(0),a=(Element)root.getElementsByTagName("direccionDespacho").item(0);var customer=new JsonObject();for(String k:List.of("nombre","rut","telefono"))customer.addProperty(k,child(c,k));var address=new JsonObject();for(String k:List.of("comuna","calleYNumero"))address.addProperty(k,child(a,k));var products=new JsonArray();var items=root.getElementsByTagName("item");
  for(int j=0;j<items.getLength();j++){Element item=(Element)items.item(j),prod=(Element)item.getElementsByTagName("producto").item(0);var p=new JsonObject();p.addProperty("sku",child(prod,"sku"));p.addProperty("name",child(prod,"nombre"));p.addProperty("quantity",new BigDecimal(child(item,"cantidad")));p.addProperty("unitPrice",new BigDecimal(child(prod,"precioLista")));products.add(p);}
  var meta=new JsonObject();meta.addProperty("originalXml",xml);return CanonicalModel.base(id,"web",child(root,"fecha"),CanonicalModel.normalizedCustomer(val(customer,"nombre"),null,val(customer,"rut"),val(customer,"telefono")),CanonicalModel.normalizedAddress(null,null,val(address,"comuna"),null,val(address,"calleYNumero")),products,null,"CLP",meta);
 }

}
