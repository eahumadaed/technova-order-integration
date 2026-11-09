package cl.technova;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MainTest {
 @Test void marketplaceCalculation(){var o=MarketplaceTranslator.marketplace(Shared.object("""
 {"id":"id1","fecha":"2026-01-01T12:00:00","cliente":{"nombre":"Ana","rut":"1-9"},"direccion":{},"items":[{"producto":"A","cantidad":2,"precioUnitario":125.50}],"shipping":{"idVenta":"id1","costoEnvio":10,"moneda":"CLP"}}
 """));assertEquals("251.00",o.get("subtotal").getAsBigDecimal().toPlainString());assertEquals("261.00",o.get("total").getAsBigDecimal().toPlainString());}
 @Test void mismatchRejected(){var o=Shared.object("""
 {"id":"id1","fecha":"2026-01-01","cliente":{},"direccion":{},"items":[{"producto":"A","cantidad":1,"precioUnitario":1}],"shipping":{"idVenta":"bad","costoEnvio":10,"moneda":"CLP"}}
 """);assertThrows(IllegalArgumentException.class,()->MarketplaceTranslator.marketplace(o));}
 @Test void webCalculation() throws Exception {var o=WebTranslator.web("""
 <pedido><fecha>2026-01-01T12:00:00</fecha><items><item><producto><sku>ABC</sku><nombre>A</nombre><precioLista>120</precioLista></producto><cantidad>2</cantidad></item></items><comprador><nombre>Ana</nombre><rut>1-9</rut></comprador><direccionDespacho><comuna>Santiago</comuna></direccionDespacho></pedido>
 ""","abc");assertEquals(240,o.get("total").getAsInt());assertTrue(o.get("shippingCost").isJsonNull());}
 @Test void xmlEntitiesRejected(){assertThrows(Exception.class,()->WebTranslator.web("<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///etc/passwd'>]><pedido>&e;</pedido>","x"));}
 @Test void validatesRealSoapEnvelope() throws Exception {
  String good="<s:Envelope xmlns:s='http://schemas.xmlsoap.org/soap/envelope/'><s:Body><x:generarBoletaResponse xmlns:x='http://example.org/'><boletaXml>&lt;DTE/&gt;</boletaXml></x:generarBoletaResponse></s:Body></s:Envelope>";
  assertTrue(BillingAdapter.validSoapResponse(good));
  assertFalse(BillingAdapter.validSoapResponse(good.replace("&lt;DTE/&gt;", "  ")));
  assertFalse(BillingAdapter.validSoapResponse(good.replace("generarBoletaResponse", "otraOperacion")));
  assertFalse(BillingAdapter.validSoapResponse("<s:Envelope xmlns:s='http://schemas.xmlsoap.org/soap/envelope/'><s:Body><s:Fault><boletaXml>fake</boletaXml></s:Fault></s:Body></s:Envelope>"));
  assertThrows(Exception.class,()->BillingAdapter.validSoapResponse("<not-xml"));
 }
}
