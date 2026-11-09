package cl.technova;

import com.sun.net.httpserver.HttpServer;
import jakarta.jms.*;
import org.apache.activemq.artemis.api.core.*;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.*;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class IntegrationTest {
 static ActiveMQServer broker;
 static ActiveMQConnectionFactory factory;
 @BeforeAll static void setup() throws Exception {
  var config=new ConfigurationImpl().setPersistenceEnabled(false).setSecurityEnabled(false).addAcceptorConfiguration(new TransportConfiguration(InVMAcceptorFactory.class.getName()));
  broker=ActiveMQServers.newActiveMQServer(config);broker.start();
  for(String name:new String[]{"eah_web_pedidos","eah_mkp_pedidos"}) {
   broker.addAddressInfo(new AddressInfo(SimpleString.of(name),RoutingType.ANYCAST));
   broker.createQueue(QueueConfiguration.of(name).setAddress(name).setRoutingType(RoutingType.ANYCAST).setDurable(true));
  }
  broker.addAddressInfo(new AddressInfo(SimpleString.of("eah_pedidos"),RoutingType.MULTICAST));
  for(String name:new String[]{"eah_facturacion","eah_bodega"})broker.createQueue(QueueConfiguration.of(name).setAddress("eah_pedidos").setRoutingType(RoutingType.MULTICAST).setDurable(true));
  factory=new ActiveMQConnectionFactory("vm://0");
 }
 @AfterAll static void close() throws Exception {if(factory!=null)factory.close();if(broker!=null)broker.stop();}
 @Test void brokerDeliveryFanoutAndRollback() throws Exception {
  try(var c=factory.createConnection();var s=c.createSession(Session.SESSION_TRANSACTED)){
   c.start();var q=s.createQueue("eah_web_pedidos");var reader=s.createConsumer(q);s.createProducer(q).send(s.createTextMessage("payload"));s.commit();
   assertEquals("payload",((TextMessage)reader.receive(3000)).getText());s.rollback();
   var redelivered=(TextMessage)reader.receive(3000);assertNotNull(redelivered);assertTrue(redelivered.getJMSRedelivered());assertEquals("payload",redelivered.getText());s.commit();
   var billing=s.createConsumer(s.createQueue("eah_pedidos::eah_facturacion"));var warehouse=s.createConsumer(s.createQueue("eah_pedidos::eah_bodega"));s.createProducer(s.createTopic("eah_pedidos")).send(s.createTextMessage("canonical"));s.commit();
   assertEquals("canonical",((TextMessage)billing.receive(3000)).getText());assertEquals("canonical",((TextMessage)warehouse.receive(3000)).getText());s.commit();
  }
 }
 @Test void bothSourcesTranslateToSameFields() throws Exception {
  var web=WebTranslator.web("<pedido><fecha>2026-01-01T10:00:00</fecha><items><item><producto><sku>A</sku><nombre>Item</nombre><precioLista>100</precioLista></producto><cantidad>2</cantidad></item></items><comprador><nombre>Test</nombre><rut>1-9</rut></comprador><direccionDespacho><comuna>X</comuna></direccionDespacho></pedido>","web-1");
  var market=MarketplaceTranslator.marketplace(Shared.object("{\"id\":\"market-1\",\"fecha\":\"2026-01-01T10:00:00\",\"cliente\":{\"nombre\":\"Test\",\"rut\":\"1-9\"},\"direccion\":{},\"items\":[{\"producto\":\"Item\",\"cantidad\":2,\"precioUnitario\":100}],\"shipping\":{\"idVenta\":\"market-1\",\"costoEnvio\":10,\"moneda\":\"CLP\"}}"));
  assertEquals(web.keySet(),market.keySet());assertEquals(200,web.get("total").getAsInt());assertEquals(210,market.get("total").getAsInt());assertEquals(web.getAsJsonObject("customer").keySet(),market.getAsJsonObject("customer").keySet());
 }
 @Test void marketplaceReportsPartialFailure() throws Exception {
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/orders/today",e->{byte[] b="[{\"id\":\"ok-1\"},{\"id\":\"failed-1\"}]".getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close();});
  server.createContext("/orders/ok-1/shipping-cost",e->{byte[] b="{\"idVenta\":\"ok-1\",\"costoEnvio\":10,\"moneda\":\"CLP\"}".getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close();});
  server.createContext("/orders/failed-1/shipping-cost",e->{e.sendResponseHeaders(500,-1);e.close();});server.start();
  try {var captured=new java.io.ByteArrayOutputStream();var previous=System.out;int result;
   try {System.setOut(new java.io.PrintStream(captured,true,StandardCharsets.UTF_8));result=MarketplaceAdapter.run("http://127.0.0.1:"+server.getAddress().getPort(),factory);} finally {System.setOut(previous);}
   assertEquals(1,result);String summary=captured.toString(StandardCharsets.UTF_8);assertTrue(summary.contains("Orders found=2 successful=1 failed=1 failedIds=[failed-1]"));
   try(var c=factory.createConnection();var s=c.createSession()){c.start();var m=(TextMessage)s.createConsumer(s.createQueue("eah_mkp_pedidos")).receive(3000);assertNotNull(m);assertEquals("ok-1",Shared.object(m.getText()).get("id").getAsString());assertEquals(10,Shared.object(m.getText()).getAsJsonObject("shipping").get("costoEnvio").getAsInt());}}
  finally {server.stop(0);}
 }
 @Test void soapCallAndAmbiguousFailure(@TempDir Path dir) throws Exception {
  var calls=new AtomicInteger();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/ok",e->{calls.incrementAndGet();String request=new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);assertTrue(request.contains("<total>200</total>"));byte[] b="<s:Envelope xmlns:s='http://schemas.xmlsoap.org/soap/envelope/'><s:Body><x:generarBoletaResponse xmlns:x='http://example.org/'><boletaXml>test</boletaXml></x:generarBoletaResponse></s:Body></s:Envelope>".getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close();});
  server.createContext("/fail",e->{calls.incrementAndGet();e.sendResponseHeaders(500,-1);e.close();});server.start();
  try {String base="http://127.0.0.1:"+server.getAddress().getPort();var good=Shared.object("{\"id\":\"bill-ok\",\"customer\":{\"name\":\"Test\",\"rut\":\"1-9\"},\"total\":200}");BillingAdapter.process(good,dir,base+"/ok");BillingAdapter.process(good,dir,base+"/ok");assertEquals(1,calls.get());assertTrue(Files.exists(dir.resolve("bill-ok.done")));
   var bad=Shared.object("{\"id\":\"bill-fail\",\"customer\":{\"name\":\"Test\",\"rut\":\"1-9\"},\"total\":200}");assertThrows(Exception.class,()->BillingAdapter.process(bad,dir,base+"/fail"));assertTrue(Files.exists(dir.resolve("bill-fail.pending")));assertThrows(java.lang.IllegalStateException.class,()->BillingAdapter.process(bad,dir,base+"/fail"));assertEquals(2,calls.get());
  } finally {server.stop(0);}
 }
}

