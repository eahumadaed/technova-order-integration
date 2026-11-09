package cl.technova;
public class Main {
 public static void main(String[] args) throws Exception {
  if(args.length!=1)throw new IllegalArgumentException("Use marketplace | web-translator | marketplace-translator | billing | warehouse");
  switch(args[0]) {
   case "marketplace" -> { int status=MarketplaceAdapter.run(); if(status!=0) System.exit(status); }
   case "web-translator" -> TranslatorRunner.translate(true);
   case "marketplace-translator" -> TranslatorRunner.translate(false);
   case "billing" -> BillingAdapter.consume();
   case "warehouse" -> WarehouseConsumer.consume();
   default -> throw new IllegalArgumentException("Unknown component");
  }
 }
}
