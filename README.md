# TechNova Order Integration

Servicio de integración de pedidos que conecta una tienda web y un marketplace con Facturación y Bodega mediante mensajería. Normaliza pedidos de distintas fuentes en un modelo JSON común y distribuye una copia independiente a cada sistema de destino.

## Arquitectura

```text
Tienda web ── XML ──> eah_web_pedidos ── traductor ──┐
                                                   ├──> eah_pedidos ──> eah_facturacion ──> SOAP
Marketplace ─ REST ─> enriquecimiento ──> eah_mkp_pedidos ── traductor ──┘          └──> eah_bodega
```

La tienda publica el XML de su evento de compra. El adapter del marketplace consulta los pedidos del día y agrega el costo de envío antes de publicarlos. Dos traductores convierten los mensajes al mismo contrato canónico. Los canales de entrada son colas ANYCAST; el canal central es una dirección MULTICAST con colas durables separadas para Facturación y Bodega. Los traductores agrupan la recepción y la publicación en una transacción JMS.

## Tecnologías

- Java 21 y Gradle 9.2.1.
- Jakarta Messaging 3.1 y Apache ActiveMQ Artemis 2.40.0.
- Gson, JAXB y cliente HTTP de Java.
- Integración REST para Marketplace y SOAP 1.1 para Facturación.
- JUnit 5 para las pruebas automatizadas.

## Estructura

```text
integracion/        Servicio, modelo canónico, adaptadores y pruebas
artemis/broker.xml  Direcciones y colas de mensajes
parches/            Adaptación de la aplicación de tienda de referencia
```

Las clases `MessagingConfiguration`, `CanonicalModel`, `MarketplaceAdapter`, `WebTranslator`, `MarketplaceTranslator`, `BillingAdapter` y `WarehouseConsumer` dividen las responsabilidades. Los componentes se ejecutan como procesos independientes del mismo artefacto Java. La implementación de Bodega es un consumidor de consola que informa los productos de cada pedido.

## Requisitos

- JDK 21 disponible en `PATH`. El Gradle Wrapper descarga Gradle 9.2.1.
- Git y ActiveMQ Artemis 2.40.0.
- Servicios compatibles con los contratos REST y SOAP descritos abajo.
- Acceso a Maven Central para las dependencias.

La configuración incluida está orientada a una demostración local. Revise autenticación, transporte y políticas del broker antes de usarla en un entorno compartido.

## Compilación y pruebas

Desde la raíz del repositorio:

```powershell
java -version
.\integracion\gradlew.bat -p .\integracion test integrationTest installDist
```

En Linux, use `./integracion/gradlew -p ./integracion test integrationTest installDist`. Las pruebas unitarias cubren cálculos, validaciones y XML. La tarea `integrationTest` utiliza Artemis embebido y un servidor HTTP SOAP local simulado para verificar entrega, distribución, redelivery, enriquecimiento, fallo parcial y recuperación. El workflow ejecuta las pruebas unitarias sin servicios externos.

## Configuración del broker

Cree una instancia nueva de Artemis 2.40.0 con su propio usuario y contraseña y copie `artemis/broker.xml` al directorio `etc` de la instancia antes de iniciarla. El archivo declara explícitamente estas direcciones:

| Dirección | Tipo | Destino |
|---|---|---|
| `eah_web_pedidos` | ANYCAST | Pedidos XML de la tienda |
| `eah_mkp_pedidos` | ANYCAST | Pedidos JSON enriquecidos |
| `eah_pedidos` | MULTICAST | Pedidos JSON canónicos |

La dirección central contiene las colas durables `eah_facturacion` y `eah_bodega`. Configure las credenciales fuera del repositorio. Para conexión autenticada, establezca `BROKER_USER` y `BROKER_PASSWORD` juntos en el entorno de cada proceso.

| Variable | Valor predeterminado | Uso |
|---|---|---|
| `BROKER_URL` | `tcp://localhost:61616` | Conexión JMS |
| `BROKER_USER`, `BROKER_PASSWORD` | Sin valor | Credenciales opcionales del broker |
| `MARKETPLACE_URL` | `http://localhost:8091` | Servicio REST |
| `SOAP_URL` | `http://localhost:8090/soap/facturacion` | Servicio de Facturación |
| `STATE_DIR` | `state` en el directorio actual | Registro local de resultados SOAP |

## Inicio

Tras compilar, inicie los consumidores en terminales independientes desde la raíz del repositorio:

```powershell
.\integracion\build\install\integracion\bin\integracion.bat web-translator
.\integracion\build\install\integracion\bin\integracion.bat marketplace-translator
.\integracion\build\install\integracion\bin\integracion.bat billing
.\integracion\build\install\integracion\bin\integracion.bat warehouse
```

Cada línea corresponde a una terminal distinta. Ejecute el adapter REST una vez cuando el marketplace y el broker estén disponibles:

```powershell
.\integracion\build\install\integracion\bin\integracion.bat marketplace
```

En Linux el ejecutable equivalente es `./integracion/build/install/integracion/bin/integracion`. Para mantener el estado de facturación entre reinicios, configure `STATE_DIR` con una ruta persistente y única para el consumidor.

## Contratos de integración

El marketplace debe exponer `GET /orders/today` y `GET /orders/{id}/shipping-cost`. El segundo endpoint entrega `idVenta`, `costoEnvio` y `moneda`; el adapter comprueba que el identificador corresponda al pedido y conserva el JSON original en el mensaje enriquecido.

Facturación debe exponer un servicio SOAP 1.1 con la operación `generarBoleta(cliente, rut, total)` y respuesta `boletaXml`. El adapter valida el envelope y la operación de respuesta antes de registrar el resultado.

El modelo canónico contiene identificador, origen, fecha sin zona horaria añadida, cliente, dirección, productos, subtotal, envío, moneda, total y metadatos del origen. Se usa `BigDecimal` para calcular los importes. En la tienda, el envío no informado se representa como `null` y el total corresponde al valor de productos de la fuente. En el marketplace, el envío se suma una vez al subtotal. El XML de la tienda se conserva en metadatos y el JSON enriquecido del marketplace en `metadata.original`.

## Tienda de referencia

`parches/tiendaweb.patch` contiene la modificación del listener de la tienda que transforma el pedido con JAXB y lo publica por JMS. Está preparado para el commit `e062844cf47706290b6c5df7a72399e2759c8210` del [repositorio de referencia](https://github.com/stgoneira/insi-eva2). Ese repositorio no declara licencia: consulte al titular antes de redistribuir sus componentes. El material de referencia se obtiene por separado y no se incluye en este repositorio.

Para preparar localmente la tienda adaptada y los servicios de referencia en Windows, desde la raíz de este repositorio:

```powershell
git clone https://github.com/stgoneira/insi-eva2.git .\vendor\referencia
git -C .\vendor\referencia checkout --detach e062844cf47706290b6c5df7a72399e2759c8210
git -C .\vendor\referencia apply --check (Resolve-Path .\parches\tiendaweb.patch)
git -C .\vendor\referencia apply (Resolve-Path .\parches\tiendaweb.patch)
Expand-Archive .\vendor\referencia\Facturacion\app-facturacion.zip .\vendor\facturacion
```

Inicie `vendor/referencia/TiendaWeb` con Gradle, el JAR de `vendor/referencia/Marketplace` con Java y `vendor/facturacion/app/bin/app.bat` en terminales separadas. Antes de usar el parche, conserve la revisión fijada y compruebe que `git apply --check` no indique conflictos.

## Entrega y recuperación

El adapter de Marketplace usa la detección de duplicados del broker; la caché es limitada y no constituye una garantía indefinida. Facturación registra las respuestas recibidas en archivos `.done` y marca con `.pending` las solicitudes cuyo resultado puede ser incierto. Si se pierde una respuesta, detenga el consumidor y concilie el pedido con el servicio antes de reintentar; revise también la DLQ. El contrato SOAP no incluye una clave de idempotencia y no permite garantizar por sí solo una única emisión.

El adapter REST informa la cantidad de pedidos encontrados, exitosos y fallidos, junto con los identificadores fallidos. Devuelve código de salida distinto de cero si algún pedido falla; Artemis rechaza los identificadores ya presentes en su caché de duplicados, que se contabilizan como fallidos. El sistema no implementa una planificación periódica propia ni un servicio completo de inventario.
