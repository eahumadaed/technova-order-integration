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
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

public class Main {
 static final Gson G = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
 static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
 static String env(String n,String d){return System.getenv().getOrDefault(n,d);}
 static ActiveMQConnectionFactory factory(){String url=env("BROKER_URL","tcp://localhost:61616");String user=System.getenv("BROKER_USER"),password=System.getenv("BROKER_PASSWORD");if((user==null)!=(password==null))throw new IllegalArgumentException("BROKER_USER and BROKER_PASSWORD must be provided together");return user==null?new ActiveMQConnectionFactory(url):new ActiveMQConnectionFactory(url,user,password);}
 static String get(String u)throws Exception{var r=HTTP.send(HttpRequest.newBuilder(URI.create(u)).timeout(Duration.ofSeconds(10)).GET().build(),HttpResponse.BodyHandlers.ofString()); if(r.statusCode()!=200)throw new IOException("HTTP "+r.statusCode()+" "+u); return r.body();}
 static JsonObject object(String s){return JsonParser.parseString(s).getAsJsonObject();}
 static String val(JsonObject o,String key){return o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsString():null;}
 static BigDecimal decimal(JsonObject o,String key){return o.get(key).getAsBigDecimal();}
 static JsonObject base(String id,String source,String date,JsonObject customer,JsonObject address,JsonArray products,BigDecimal shipping,String currency,JsonObject metadata){
  if(id==null||id.isBlank()||date==null||products.isEmpty())throw new IllegalArgumentException("Missing order fields");
  BigDecimal subtotal=BigDecimal.ZERO; for(JsonElement e:products){var p=e.getAsJsonObject(); var q=decimal(p,"quantity"); var price=decimal(p,"unitPrice"); if(q.signum()<=0||price.signum()<0)throw new IllegalArgumentException("Invalid amount"); BigDecimal line=price.multiply(q); p.addProperty("subtotal",line); subtotal=subtotal.add(line);}
  if(shipping!=null&&shipping.signum()<0)throw new IllegalArgumentException("Invalid shipping");
  var o=new JsonObject();o.addProperty("id",id);o.addProperty("originalId",source.equals("marketplace")?id:null);o.addProperty("source",source);o.addProperty("purchaseDate",date);o.add("customer",customer);o.add("shippingAddress",address);o.add("products",products);o.addProperty("subtotal",subtotal); if(shipping==null)o.add("shippingCost",JsonNull.INSTANCE);else o.addProperty("shippingCost",shipping); o.addProperty("currency",currency);if(shipping==null)o.addProperty("total",subtotal);else o.addProperty("total",subtotal.add(shipping));o.add("metadata",metadata);return o;
 }
 static JsonObject normalizedCustomer(String first,String last,String rut,String phone){var c=new JsonObject();c.addProperty("name",first);c.addProperty("lastName",last);c.addProperty("rut",rut);c.addProperty("phone",phone);return c;}
 static JsonObject normalizedAddress(String street,String number,String district,String city,String full){var a=new JsonObject();a.addProperty("street",street);a.addProperty("number",number);a.addProperty("district",district);a.addProperty("city",city);a.addProperty("streetAndNumber",full);return a;}
 static JsonObject marketplace(JsonObject source){
  var customer=source.getAsJsonObject("cliente");var address=source.getAsJsonObject("direccion");var products=new JsonArray();
  for(var e:source.getAsJsonArray("items")){var i=e.getAsJsonObject();var p=new JsonObject();p.addProperty("sku",(String)null);p.addProperty("name",val(i,"producto"));p.addProperty("quantity",decimal(i,"cantidad"));p.addProperty("unitPrice",decimal(i,"precioUnitario"));products.add(p);}
  var ship=source.getAsJsonObject("shipping");if(!Objects.equals(val(source,"id"),val(ship,"idVenta")))throw new IllegalArgumentException("Shipping order mismatch");
  var meta=new JsonObject();meta.add("original",source.deepCopy());return base(val(source,"id"),"marketplace",val(source,"fecha"),normalizedCustomer(val(customer,"nombre"),val(customer,"apellido"),val(customer,"rut"),null),normalizedAddress(val(address,"calle"),val(address,"numero"),val(address,"comuna"),val(address,"ciudad"),null),products,decimal(ship,"costoEnvio"),val(ship,"moneda"),meta);
 }
 static String child(Element e,String name){var n=e.getElementsByTagName(name);return n.getLength()==0?null:n.item(0).getTextContent();}
 static JsonObject web(String xml,String id)throws Exception{
  var f=DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setFeature("http://xml.org/sax/features/external-general-entities",false);f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);f.setXIncludeAware(false);f.setExpandEntityReferences(false);
  Element root=f.newDocumentBuilder().parse(new InputSource(new StringReader(xml))).getDocumentElement();
  Element c=(Element)root.getElementsByTagName("comprador").item(0),a=(Element)root.getElementsByTagName("direccionDespacho").item(0);var customer=new JsonObject();for(String k:List.of("nombre","rut","telefono"))customer.addProperty(k,child(c,k));var address=new JsonObject();for(String k:List.of("comuna","calleYNumero"))address.addProperty(k,child(a,k));var products=new JsonArray();var items=root.getElementsByTagName("item");
  for(int j=0;j<items.getLength();j++){Element item=(Element)items.item(j),prod=(Element)item.getElementsByTagName("producto").item(0);var p=new JsonObject();p.addProperty("sku",child(prod,"sku"));p.addProperty("name",child(prod,"nombre"));p.addProperty("quantity",new BigDecimal(child(item,"cantidad")));p.addProperty("unitPrice",new BigDecimal(child(prod,"precioLista")));products.add(p);}
  var meta=new JsonObject();meta.addProperty("originalXml",xml);return base(id,"web",child(root,"fecha"),normalizedCustomer(val(customer,"nombre"),null,val(customer,"rut"),val(customer,"telefono")),normalizedAddress(null,null,val(address,"comuna"),null,val(address,"calleYNumero")),products,null,"CLP",meta);
 }
 static void marketplaceAdapter()throws Exception{
  String url=env("MARKETPLACE_URL","http://localhost:8091");var orders=JsonParser.parseString(get(url+"/orders/today")).getAsJsonArray();
  try(var f=factory();var c=f.createConnection();var s=c.createSession(Session.SESSION_TRANSACTED)){c.start();var producer=s.createProducer(s.createQueue("eah_mkp_pedidos"));for(var e:orders){try{var o=e.getAsJsonObject().deepCopy();String id=val(o,"id");if(id==null)throw new IllegalArgumentException("Missing id");var ship=object(get(url+"/orders/"+id+"/shipping-cost"));if(!id.equals(val(ship,"idVenta")))throw new IllegalArgumentException("Shipping order mismatch");o.add("shipping",ship);var m=s.createTextMessage(G.toJson(o));m.setStringProperty("_AMQ_DUPL_ID","marketplace:"+id);m.setStringProperty("orderId",id);producer.send(m);s.commit();System.out.println("Published enriched order "+id+" "+G.toJson(o));}catch(Exception ex){s.rollback();System.err.println("Order failed: "+ex);}}}
 }
 static void translate(boolean isWeb)throws Exception{
  try(var f=factory();var c=f.createConnection();var s=c.createSession(Session.SESSION_TRANSACTED)){var in=s.createQueue(isWeb?"eah_web_pedidos":"eah_mkp_pedidos");var consumer=s.createConsumer(in);var producer=s.createProducer(s.createTopic("eah_pedidos"));c.start();while(true){Message m=consumer.receive();try{String payload=((TextMessage)m).getText();String id=m.getStringProperty("orderId");if(isWeb&&(id==null||id.isBlank()))throw new IllegalArgumentException("Missing orderId");var o=isWeb?web(payload,id):marketplace(object(payload));var out=s.createTextMessage(G.toJson(o));out.setStringProperty("orderId",val(o,"id"));producer.send(out);s.commit();System.out.println("Translated order "+val(o,"id")+" to eah_pedidos "+G.toJson(o));}catch(Exception ex){s.rollback();System.err.println("Translation failed: "+ex);Thread.sleep(2000);}}}
 }
 static boolean validSoapResponse(String xml) throws Exception {
  var f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setFeature("http://xml.org/sax/features/external-general-entities",false);f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);f.setXIncludeAware(false);f.setExpandEntityReferences(false);
  Element envelope=f.newDocumentBuilder().parse(new InputSource(new StringReader(xml))).getDocumentElement();String soap="http://schemas.xmlsoap.org/soap/envelope/";
  if(!soap.equals(envelope.getNamespaceURI())||!"Envelope".equals(envelope.getLocalName()))return false;
  Element body=null;for(Node n=envelope.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element e&&soap.equals(e.getNamespaceURI())&&"Body".equals(e.getLocalName())){if(body!=null)return false;body=e;}
  if(body==null)return false;Element response=null;for(Node n=body.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element e){if(soap.equals(e.getNamespaceURI())&&"Fault".equals(e.getLocalName()))return false;if(response!=null)return false;response=e;}
  if(response==null||!"http://example.org/".equals(response.getNamespaceURI())||!"generarBoletaResponse".equals(response.getLocalName()))return false;
  for(Node n=response.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element e&&"boletaXml".equals(e.getLocalName())&&(e.getNamespaceURI()==null||e.getNamespaceURI().isEmpty()))return !e.getTextContent().isBlank();return false;
 }
 static String xmlEscape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");}
 static void consume(boolean billing)throws Exception{
  Path ledger=Path.of(env("STATE_DIR","state"));Files.createDirectories(ledger);
  try(var f=factory();var c=f.createConnection();var s=c.createSession(Session.SESSION_TRANSACTED)){var consumer=s.createConsumer(s.createQueue("eah_pedidos::"+(billing?"eah_facturacion":"eah_bodega")));c.start();while(true){Message m=consumer.receive();try{var o=object(((TextMessage)m).getText());String id=val(o,"id");if(id==null||!id.matches("[A-Za-z0-9_-]+"))throw new IllegalArgumentException("Invalid id");if(!billing){System.out.println("Warehouse order "+id+" products="+o.get("products"));s.commit();continue;}
    Path done=ledger.resolve(id+".done"),pending=ledger.resolve(id+".pending");if(Files.exists(done)){s.commit();continue;}if(Files.exists(pending))throw new java.lang.IllegalStateException("Ambiguous previous SOAP result; manual reconciliation required: "+id);
    var customer=o.getAsJsonObject("customer");String name=val(customer,"name");String last=val(customer,"lastName");if(last!=null)name=name+" "+last;String rut=val(customer,"rut");if(name==null||rut==null||o.get("total").isJsonNull())throw new IllegalArgumentException("Billing requires name, RUT and known total: "+id);
    long total=decimal(o,"total").longValueExact();String body="<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body><ns:generarBoleta xmlns:ns=\"http://example.org/\"><cliente>"+xmlEscape(name)+"</cliente><rut>"+xmlEscape(rut)+"</rut><total>"+total+"</total></ns:generarBoleta></soap:Body></soap:Envelope>";
    Files.writeString(pending,"SOAP started; reconcile before retry",StandardOpenOption.CREATE_NEW);var request=HttpRequest.newBuilder(URI.create(env("SOAP_URL","http://localhost:8090/soap/facturacion"))).timeout(Duration.ofSeconds(10)).header("Content-Type","text/xml; charset=utf-8").POST(HttpRequest.BodyPublishers.ofString(body)).build();var response=HTTP.send(request,HttpResponse.BodyHandlers.ofString());if(response.statusCode()!=200||!validSoapResponse(response.body()))throw new IOException("SOAP failure or ambiguous response "+response.statusCode());Files.writeString(done,response.body(),StandardOpenOption.CREATE_NEW);Files.delete(pending);System.out.println("Billing response order "+id+" "+response.body());s.commit();
   }catch(Exception ex){s.rollback();System.err.println("Consumer failed: "+ex);Thread.sleep(3000);}}}
 }
 public static void main(String[] args)throws Exception{if(args.length!=1)throw new IllegalArgumentException("Use marketplace | web-translator | marketplace-translator | billing | warehouse");switch(args[0]){case "marketplace"->marketplaceAdapter();case "web-translator"->translate(true);case "marketplace-translator"->translate(false);case "billing"->consume(true);case "warehouse"->consume(false);default->throw new IllegalArgumentException("Unknown component");}}
}

