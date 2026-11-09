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

final class BillingAdapter {
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
 static void process(JsonObject order, Path ledger, String endpoint) throws Exception {
  String id=val(order,"id");if(id==null||!id.matches("[A-Za-z0-9_-]+"))throw new IllegalArgumentException("Invalid id");
  Files.createDirectories(ledger);Path done=ledger.resolve(id+".done"),pending=ledger.resolve(id+".pending");
  if(Files.exists(done))return;if(Files.exists(pending))throw new java.lang.IllegalStateException("Ambiguous previous SOAP result; manual reconciliation required: "+id);
  var customer=order.getAsJsonObject("customer");String name=val(customer,"name"),last=val(customer,"lastName");if(last!=null)name=name+" "+last;String rut=val(customer,"rut");
  if(name==null||rut==null||order.get("total").isJsonNull())throw new IllegalArgumentException("Billing requires name, RUT and known total: "+id);
  long total=decimal(order,"total").longValueExact();
  String body="<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body><ns:generarBoleta xmlns:ns=\"http://example.org/\"><cliente>"+xmlEscape(name)+"</cliente><rut>"+xmlEscape(rut)+"</rut><total>"+total+"</total></ns:generarBoleta></soap:Body></soap:Envelope>";
  Files.writeString(pending,"SOAP started; reconcile before retry",StandardOpenOption.CREATE_NEW);
  var request=HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(10)).header("Content-Type","text/xml; charset=utf-8").POST(HttpRequest.BodyPublishers.ofString(body)).build();
  var response=HTTP.send(request,HttpResponse.BodyHandlers.ofString());
  if(response.statusCode()!=200||!validSoapResponse(response.body()))throw new IOException("SOAP failure or ambiguous response "+response.statusCode());
  Files.writeString(done,response.body(),StandardOpenOption.CREATE_NEW);Files.delete(pending);System.out.println("Billing response order "+id+" "+response.body());
 }
 static void consume()throws Exception{
  Path ledger=Path.of(env("STATE_DIR","state"));Files.createDirectories(ledger);
  try(var f=factory();var c=f.createConnection();var s=c.createSession(Session.SESSION_TRANSACTED)){
   var consumer=s.createConsumer(s.createQueue("eah_pedidos::eah_facturacion"));c.start();
   while(true){Message m=consumer.receive();try{process(object(((TextMessage)m).getText()),ledger,env("SOAP_URL","http://localhost:8090/soap/facturacion"));s.commit();}catch(Exception ex){s.rollback();System.err.println("Consumer failed: "+ex);Thread.sleep(3000);}}
  }
 }
}
